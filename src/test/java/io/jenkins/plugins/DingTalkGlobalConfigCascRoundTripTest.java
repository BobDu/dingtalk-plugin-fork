package io.jenkins.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Runs the configuration through the whole Configuration as Code cycle: apply it, validate and apply
 * it again through the web UI, then restart and check it came back.
 *
 * <p>The restart is the part that {@link DingTalkGlobalConfigCascTest} cannot cover. Configuration as
 * Code writes through a {@code BulkChange} on the descriptor, so what it configures has to survive
 * as the descriptor's own saved state and not only as something re-applied from the yaml on boot.
 *
 * <p>The web UI half is worth having too: it is the path an administrator takes to apply a
 * configuration by hand, and it goes through {@code checkNewSource} first, which fails on an
 * attribute the running instance does not recognise.
 */
class DingTalkGlobalConfigCascRoundTripTest extends AbstractRoundTripTest {

  @Override
  protected String configResource() {
    return "casc-global-config.yaml";
  }

  @Override
  protected String stringInLogExpected() {
    return "robot-from-casc";
  }

  @Override
  protected void assertConfiguredAsExpected(JenkinsRule j, String configContent) {
    DingTalkGlobalConfig config = DingTalkGlobalConfig.getInstance();

    assertTrue(config.isVerbose(), "verbose");
    assertEquals(1, config.getRobotConfigs().size(), "robots");
    assertEquals("robot-from-casc", config.getRobotConfigs().get(0).getId());
    assertEquals(
        "https://oapi.dingtalk.com/robot/send?access_token=casc-token",
        config.getRobotConfigs().get(0).getWebhook());
  }
}
