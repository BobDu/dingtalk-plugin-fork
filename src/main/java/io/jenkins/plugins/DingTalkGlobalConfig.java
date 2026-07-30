package io.jenkins.plugins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import io.jenkins.plugins.DingTalkRobotConfig.DingTalkRobotConfigDescriptor;
import io.jenkins.plugins.enums.NoticeOccasionEnum;
import io.jenkins.plugins.model.NoticeOccasionOption;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import io.jenkins.plugins.service.DingTalkService;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.ToString;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * 全局配置
 *
 * @author liuwei
 */
@SuppressWarnings("unused")
@Getter
@ToString
@Extension
// Configuration as Code accepts any of these names and writes the first, and it raises an
// administrative monitor for a name matched through any of the others. dingTalkGlobalConfig is the
// one it derived from the class name for 2.4.7 and earlier, and so the only one that has ever
// resolved to these settings in a release: dingtalk was added along with the change that made them
// unreachable. It leads for that reason, so that a configuration written back then keeps loading
// without also being told it is obsolete.
@Symbol({"dingTalkGlobalConfig", "dingtalk"})
public class DingTalkGlobalConfig extends Descriptor<DingTalkGlobalConfig> implements
    Describable<DingTalkGlobalConfig> {
  private static final int NOTICE_OCCASION_COLUMNS = 3;

  /**
   * 网络代理
   */
  private DingTalkProxyConfig proxyConfig;

  /**
   * 是否打印详细日志
   */
  private boolean verbose;

  /**
   * 通知时机
   */
  private Set<String> noticeOccasions = Arrays.stream(NoticeOccasionEnum.values()).map(Enum::name)
      .collect(Collectors.toSet());

  /**
   * 机器人配置列表
   */
  private ArrayList<DingTalkRobotConfig> robotConfigs = new ArrayList<>();

  /**
   * 获取网络代理
   *
   * @return proxy
   */
  public Proxy getProxy() {
    if (proxyConfig == null) {
      return null;
    }
    return proxyConfig.getProxy();
  }

  @DataBoundSetter
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  @DataBoundSetter
  public void setNoticeOccasions(Set<String> noticeOccasions) {
    this.noticeOccasions = noticeOccasions;
  }

  @DataBoundSetter
  public void setProxyConfig(DingTalkProxyConfig proxyConfig) {
    this.proxyConfig = proxyConfig;
  }

  @DataBoundSetter
  public void setRobotConfigs(ArrayList<DingTalkRobotConfig> robotConfigs) {
    DingTalkService.getInstance().resetSenders();
    this.robotConfigs = robotConfigs;
  }

  @DataBoundConstructor
  public DingTalkGlobalConfig(DingTalkProxyConfig proxyConfig, boolean verbose,
      Set<String> noticeOccasions, ArrayList<DingTalkRobotConfig> robotConfigs) {
    this.proxyConfig = proxyConfig;
    this.verbose = verbose;
    this.noticeOccasions = noticeOccasions;
    this.robotConfigs = robotConfigs;
  }

  public DingTalkGlobalConfig() {
    super(self());
    this.load();
  }

  @Override
  public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
    // Check configuration permission
    Jenkins.get().checkPermission(DingTalkPermissions.CONFIGURE);
    Object robotConfigObj = json.get("robotConfigs");

    if (robotConfigObj == null) {
      json.put("robotConfigs", new JSONArray());
    } else {
      JSONArray robotConfigs = JSONArray.fromObject(robotConfigObj);
      robotConfigs.removeIf(item -> {
        JSONObject jsonObject = JSONObject.fromObject(item);
        String webhook = jsonObject.getString("webhook");
        return StringUtils.isEmpty(webhook);
      });
    }
    req.bindJSON(this, json);
    this.save();
    return super.configure(req, json);
  }

  static List<List<NoticeOccasionOption>> buildNoticeOccasionRows() {
    List<NoticeOccasionOption> options = Arrays.stream(NoticeOccasionEnum.values())
        .map(item -> new NoticeOccasionOption(item.name(), item.getDesc()))
        .collect(Collectors.toList());
    List<List<NoticeOccasionOption>> rows = new ArrayList<>();
    for (int i = 0; i < options.size(); i += NOTICE_OCCASION_COLUMNS) {
      int end = Math.min(i + NOTICE_OCCASION_COLUMNS, options.size());
      rows.add(options.subList(i, end));
    }
    return rows;
  }

  public List<List<NoticeOccasionOption>> getNoticeOccasionRows() {
    return buildNoticeOccasionRows();
  }

  @Override
  public Descriptor<DingTalkGlobalConfig> getDescriptor() {
    return this;
  }

  /**
   * Makes this descriptor part of the unclassified global configuration.
   *
   * <p>Configuration as Code reads the {@code unclassified} root from the descriptors that report a
   * global config page ({@code GlobalConfigurationCategoryConfigurator}), which is what
   * {@link jenkins.model.GlobalConfiguration} provides by returning its config page here. This class
   * stopped extending that when the settings moved to their own management page, and with it the
   * plugin silently dropped out of {@code unclassified} — the page is still the same one, so it is
   * reported the same way. {@link HideFromGlobalConfigurationPage} keeps it off /configure.
   */
  @Override
  public String getGlobalConfigPage() {
    return getConfigPage();
  }

  /**
   * Keeps the settings out of Jenkins' own configuration page.
   *
   * <p>{@link #getGlobalConfigPage()} would otherwise place them there as well as on the management
   * page, which is both a duplicate and broken: the robot form's nested lists resolve their
   * descriptors from the surrounding context, and under /configure that context is Jenkins itself.
   * Only the global configuration listings pass a Jenkins context to a visibility filter, so this
   * hides the descriptor from them and from nothing else — including from the submit loop, which
   * would otherwise hand {@link #configure} the empty form it never rendered.
   */
  @Extension
  public static class HideFromGlobalConfigurationPage extends DescriptorVisibilityFilter {

    @Override
    public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
      return !(context instanceof Jenkins && descriptor instanceof DingTalkGlobalConfig);
    }
  }

  /**
   * `网络代理` 配置页面
   *
   * @return 网络代理配置页面
   */
  public DingTalkProxyConfig getDingTalkProxyConfigDescriptor() {
    return Jenkins.get().getDescriptorByType(DingTalkProxyConfig.class);
  }

  /**
   * `机器人` 配置页面
   *
   * @return 机器人配置页面
   */
  public DingTalkRobotConfigDescriptor getDingTalkRobotConfigDescriptor() {
    return Jenkins.get().getDescriptorByType(DingTalkRobotConfigDescriptor.class);
  }

  /**
   * 获取安全配置描述符
   *
   * @return 安全策略描述符
   */
  public ArrayList<Descriptor> getSecurityPolicyConfigsDescriptors() {
    DingTalkRobotConfigDescriptor descriptor = getDingTalkRobotConfigDescriptor();
    if (descriptor == null) {
      return new ArrayList<>();
    }
    return descriptor.getSecurityPolicyConfigsDescriptors();
  }


  /**
   * 获取全局配置信息
   *
   * @return 全局配置信息
   */
  public static DingTalkGlobalConfig getInstance() {
    return Jenkins.get().getDescriptorByType(DingTalkGlobalConfig.class);
  }

  public static Optional<DingTalkRobotConfig> getRobot(String robotId) {
    return getInstance().robotConfigs.stream().filter(item -> Objects.equals(item.getId(), robotId)).findAny();
  }
}
