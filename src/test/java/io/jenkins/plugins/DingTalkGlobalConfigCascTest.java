package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AdministrativeMonitor;
import hudson.model.Descriptor;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.Util;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.enums.NoticeOccasionEnum;
import java.net.Proxy;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

/**
 * Covers configuring the plugin with Configuration as Code.
 *
 * <p>The settings are read from the {@code unclassified} root, which Configuration as Code builds
 * from the descriptors that report a global config page. Losing that page is how the plugin dropped
 * out of it, so these cases pin both that the settings are reachable at all and the name they are
 * reachable under — an installation upgrading from 2.4.7 has that name written down, and it is the
 * only one that has ever resolved to these settings in a release.
 */
@WithJenkinsConfiguredWithCode
class DingTalkGlobalConfigCascTest {

  /**
   * Whether Configuration as Code has anything to complain about.
   *
   * <p>Looked up by name because the monitor it raises is {@code @Restricted(NoExternalUse)}. An
   * attribute matched through one of its alternative names raises it, which is the whole reason the
   * order of the names on {@code @Symbol} matters.
   */
  private static boolean casCRaisedAWarning() {
    return AdministrativeMonitor.all().stream()
        .filter(monitor -> monitor.getClass().getName().equals(
            "io.jenkins.plugins.casc.ObsoleteConfigurationMonitor"))
        .anyMatch(AdministrativeMonitor::isActivated);
  }

  @Test
  @ConfiguredWithCode("casc-global-config.yaml")
  void appliesEveryConfiguredValue(JenkinsConfiguredWithCodeRule r) {
    DingTalkGlobalConfig config = DingTalkGlobalConfig.getInstance();

    assertTrue(config.isVerbose(), "verbose");
    assertEquals(
        Set.of(NoticeOccasionEnum.START.name(), NoticeOccasionEnum.FAILURE.name()),
        config.getNoticeOccasions(),
        "the configured occasions replace the default of every occasion");

    DingTalkProxyConfig proxy = config.getProxyConfig();
    assertNotNull(proxy, "proxyConfig");
    assertEquals(Proxy.Type.HTTP, proxy.getType());
    assertEquals("proxy.example.com", proxy.getHost());
    assertEquals(8080, proxy.getPort());

    assertEquals(1, config.getRobotConfigs().size(), "robots");
    DingTalkRobotConfig robot = config.getRobotConfigs().get(0);
    assertEquals("robot-from-casc", robot.getId());
    assertEquals("Robot from CasC", robot.getName());
    assertEquals(
        "https://oapi.dingtalk.com/robot/send?access_token=casc-token",
        robot.getWebhook(),
        "the webhook is stored as a secret but has to come back out as the address to post to");

    List<DingTalkSecurityPolicyConfig> policies = robot.getSecurityPolicyConfigs();
    assertEquals(2, policies.size(), "security policies");
    assertTrue(policies.get(0) instanceof KeySecurityPolicyConfig, "KEY resolves to its own type");
    assertEquals("jenkins-keyword", policies.get(0).getValue());
    assertTrue(
        policies.get(1) instanceof SecretSecurityPolicyConfig, "SECRET resolves to its own type");
    assertEquals("SECsigningsecret", policies.get(1).getValue());

    assertFalse(
        casCRaisedAWarning(),
        "an installation that had this configuration working before 2.4.8 must be able to keep it "
            + "as it is, without being told the name it uses is obsolete");
  }

  @Test
  @ConfiguredWithCode("casc-global-config-short-name.yaml")
  void alsoAcceptsTheShortName(JenkinsConfiguredWithCodeRule r) {
    DingTalkGlobalConfig config = DingTalkGlobalConfig.getInstance();

    assertTrue(config.isVerbose(), "verbose");
    assertEquals(1, config.getRobotConfigs().size(), "robots");
    assertEquals("robot-from-short-name", config.getRobotConfigs().get(0).getId());
  }

  @Test
  @ConfiguredWithCode("casc-global-config.yaml")
  void exportsWhatWasConfigured(JenkinsConfiguredWithCodeRule r) throws Exception {
    ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
    String exported =
        Util.toYamlString(Util.getUnclassifiedRoot(context).get("dingTalkGlobalConfig"));

    assertTrue(exported.contains("verbose: true"), exported);
    assertTrue(exported.contains("id: \"robot-from-casc\""), exported);
    assertTrue(exported.contains("host: \"proxy.example.com\""), exported);
    // Both are held as a Secret, and Configuration as Code masks those on the way out rather than
    // writing them to whoever asked for the export.
    assertFalse(exported.contains("casc-token"), exported);
    assertFalse(exported.contains("SECsigningsecret"), exported);
  }

  @Test
  void keepsTheSettingsOffJenkinsOwnConfigurationPage(JenkinsConfiguredWithCodeRule r) {
    Descriptor<?> descriptor = Jenkins.get().getDescriptorByType(DingTalkGlobalConfig.class);

    assertNotNull(
        descriptor.getGlobalConfigPage(),
        "reporting a global config page is what puts the settings in unclassified");
    assertFalse(
        hudson.Functions.getSortedDescriptorsForGlobalConfigUnclassified().contains(descriptor),
        "the settings have their own management page, so /configure must not offer them too");
  }
}
