# Gradle Publishing Plugin

![Java17](https://img.shields.io/badge/JDK-17+-success.svg)
[![Apache License 2.0](https://img.shields.io/github/license/TAKETODAY/gradle-publishing-plugin?color=blue)](./LICENSE)
[![Release](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/release.yaml/badge.svg)](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/release.yaml)
[![CI](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/ci.yaml/badge.svg)](https://github.com/TAKETODAY/gradle-publishing-plugin/actions/workflows/ci.yaml)

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
