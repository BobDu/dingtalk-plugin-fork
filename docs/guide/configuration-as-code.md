# Configuration as Code

全局配置（通知时机、网络代理、机器人列表）可以交给 [Configuration as Code](https://plugins.jenkins.io/configuration-as-code/)
管理，配置写在 `unclassified.dingTalkGlobalConfig` 下面。

::: warning

2.4.8 起的一段时间里插件读不到这段配置，会报
`Invalid configuration elements for type class jenkins.model.GlobalConfigurationCategory$Unclassified`，
该问题已修复，请使用最新版插件。

:::

## 完整示例

```yaml
unclassified:
  dingTalkGlobalConfig:
    verbose: false
    noticeOccasions:
      - "START"
      - "SUCCESS"
      - "FAILURE"
    proxyConfig:
      type: "HTTP"
      host: "proxy.example.com"
      port: 8080
    robotConfigs:
      - id: "prod-robot"
        name: "生产环境"
        webhook: "https://oapi.dingtalk.com/robot/send?access_token=xxxxxx"
        securityPolicyConfigs:
          - type: "SECRET"
            value: "SECxxxxxx"
      - id: "test-robot"
        name: "测试环境"
        webhook: "https://oapi.dingtalk.com/robot/send?access_token=yyyyyy"
        securityPolicyConfigs:
          - type: "KEY"
            value: "Jenkins"
```

## 字段说明

| 字段                 | 说明                                                                        |
|--------------------|---------------------------------------------------------------------------|
| `verbose`          | 是否打印详细日志，对应全局配置里的 `详细日志`                                                  |
| `noticeOccasions`  | 默认通知时机，取值见[通知时机](./getting-started.md#通知时机)：`START` `SUCCESS` `FAILURE` `ABORTED` `UNSTABLE` `NOT_BUILT` |
| `proxyConfig.type` | `DIRECT`（不走代理）、`HTTP`、`SOCKS`                                             |
| `robotConfigs`     | 机器人列表，字段与页面上一一对应                                                          |
| `securityPolicyConfigs` | 安全策略，`type` 取 `KEY`（自定义关键词）或 `SECRET`（加签），`value` 填对应的关键词或密钥            |

不写的字段保持默认值，例如省略 `noticeOccasions` 就是六种时机全选。

## 机器人的 `id` 必须自己写

`id` 是项目配置和 pipeline 里 `robot:` 引用机器人的依据。留空的话插件会生成一个随机 UUID，
每次重新应用配置都可能换一个，已有的项目配置就会指向一个不存在的机器人。用 JCasC 管理时请显式写 `id`。

## webhook 与密钥

`webhook` 和 `securityPolicyConfigs[].value` 都以 Secret 形式保存，可以用 JCasC 的变量插值，
不必把明文写进 YAML：

```yaml
        webhook: "${DINGTALK_WEBHOOK}"
```

反过来，`Manage Jenkins` → `Configuration as Code` → `Download configuration`
导出的内容里这两项是 `****`（JCasC 对 Secret 字段的统一处理），导出结果不能直接原样应用回去，
需要自己把真实值填回来。

## 也接受 `dingtalk`

配置键也可以写成 `unclassified.dingtalk`，但这样 Jenkins 会提示一条
`'dingtalk' is an obsolete attribute name`。推荐用 `dingTalkGlobalConfig`。

## 配置页面在哪

不用 JCasC 时，这些配置在 `Manage Jenkins` → `钉钉`。
