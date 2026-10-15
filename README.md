# Gradle Publishing Plugin

![Java17](https://img.shields.io/badge/JDK-17+-success.svg)
[![Apache License 2.0](https://img.shields.io/github/license/TAKETODAY/gradle-publishing-plugin?color=blue)](./LICENSE)
[![Release](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/release.yaml/badge.svg)](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/release.yaml)
[![CI](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/ci.yaml/badge.svg)](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/ci.yaml)

### 发布配置

通过 `mavenCentral` 配置块设置发布选项（此配置块在 0.0.3 之后的版本提供）：

```groovy
import java.time.Duration
import cn.taketoday.publishing.PublishingType

mavenCentral {
  // 默认读取 mavenCentralUsername / mavenCentralPassword，也可映射其他属性
  credentials {
    username = providers.gradleProperty('myCentralUsername').get()
    password = providers.gradleProperty('myCentralPassword').get()
  }
  publishingType = PublishingType.AUTOMATIC
  waitUntilFullyPublished = true
  statusRequestInterval = Duration.ofSeconds(3)
  maxStatusRetries = 3
  baseUrl = uri('https://central.sonatype.com/')
  deploymentName = "${group}:${project.name}:${version}"
}
```

| 选项                      | 默认值                          | 说明                                                                             |
|---------------------------|---------------------------------|----------------------------------------------------------------------------------|
| `publishingType`          | `AUTOMATIC`                     | `AUTOMATIC` 自动发布；`USER_MANAGED` 校验后等待手动发布                          |
| `credentials`             | Gradle 凭据 `mavenCentral`      | `PasswordCredentials`，默认读取 `mavenCentralUsername` / `mavenCentralPassword`  |
| `waitUntilFullyPublished` | `false`                         | `true` 时等待 `PUBLISHED`；默认到 `PUBLISHING` 即完成。`USER_MANAGED` 时忽略此项 |
| `statusRequestInterval`   | 1 秒                            | 状态轮询及失败重试间隔，至少 1 毫秒                                              |
| `maxStatusRetries`        | `3`                             | 状态请求连续失败后的重试次数；`0` 表示不重试，成功请求后计数重置                 |
| `baseUrl`                 | `https://central.sonatype.com/` | Publisher API 基础地址                                                           |
| `deploymentName`          | `group:name:version`            | 部署名称                                                                         |

所有发布选项统一通过 `mavenCentral {}` 配置，上传任务直接使用该扩展，不再提供独立的任务级发布配置。
凭据默认通过以下 Gradle 项目属性提供；`credentials {}` 可覆盖默认值，也可以将已有的
`PasswordCredentials` 对象或 `Provider<PasswordCredentials>` 设置到 `credentials` 属性。
环境变量可通过 `providers.environmentVariable('MY_CENTRAL_USERNAME').get()` 读取。

#### 发布模式

`publishingType` 是 Sonatype 上传 API 的原生参数：

- `AUTOMATIC`：校验通过后自动发布。
- `USER_MANAGED`：校验通过后进入 `VALIDATED`，由用户在 Portal 或调用发布 API 确认发布。

发布模式统一使用 `publishingType`。旧任务配置 `publish=true` 改为
`publishingType=PublishingType.AUTOMATIC`，`publish=false` 改为 `publishingType=PublishingType.USER_MANAGED`。
`USER_MANAGED` 模式下忽略 `waitUntilFullyPublished`，任务在 `VALIDATED` 时完成。

Sonatype API 省略此参数时默认 `USER_MANAGED`；本插件为兼容现有行为默认自动发布。
参考：[官方 Publisher API 文档](https://central.sonatype.org/publish/publish-portal-api/)。

### Maven Central Portal 凭据

你需要为 Maven Central Portal 创建一个用户令牌：

https://central.sonatype.com/account

将项目属性 `mavenCentralUsername` 和 `mavenCentralPassword` 设置为你的用户令牌信息。

- 通过命令行设置凭据

```shell
./gradlew publishToMavenCentral -PmavenCentralUsername=<用户名> -PmavenCentralPassword=<密码>
```

- 通过环境变量设置凭据

```shell
export ORG_GRADLE_PROJECT_mavenCentralUsername=<令牌用户名>
export ORG_GRADLE_PROJECT_mavenCentralPassword=<令牌密码>
```

- 通过 `~/.gradle/gradle.properties` 设置凭据

```properties
mavenCentralUsername=<令牌用户名>
mavenCentralPassword=<令牌密码>
```
