# 构建说明

## 环境

- JDK 17。
- Android SDK Platform 35、Build Tools 36.0.0。
- Gradle Wrapper 9.1.0，配置见 gradle/wrapper/gradle-wrapper.properties。
- Protobuf Gradle Plugin 0.10.0，使用其 AGP 9 兼容支持。兼容开关 `android.newDsl=false`、`android.builtInKotlin=false` 已写入 gradle.properties。
- 首次构建需要访问 Google Maven、Maven Central、Gradle Plugin Portal 和 JitPack。已有完整依赖缓存时可追加 --offline。

克隆仓库并进入根目录，配置 Android SDK 路径（local.properties 或 ANDROID_HOME）。

```sh
bash scripts/build-release.sh
```

脚本默认构建开启 R8 和资源精简的签名 Release。不要在发布构建中传入 `-PdisableR8=true`；Debug 和无 R8 包仅供诊断，体积会显著增大。手动构建等价命令：

```sh
bash gradlew :app:assembleRelease -PreleaseVersionName=0.1.4 -PreleaseVersionCode=261
```

Windows PowerShell 可执行：

```powershell
.\scripts\build-release.ps1
```

两个脚本都会检查 APK 是否超过 10 MiB，超过时停止导出发布产物。Linux/macOS 可通过 `RELEASE_VERSION_NAME` / `RELEASE_VERSION_CODE` 或追加 Gradle 参数修改版本；PowerShell 用 `-VersionName` / `-VersionCode`。

源码 ZIP 不需要 .git 也可构建；显式版本参数避免依赖本地提交数。

## 签名

本地未配置 release 密钥时沿用 Android 调试签名，仅适合自行构建的测试包，不能用于覆盖本仓库已发布的 APK。公开发布必须核对原发布证书，不能将调试签名包作为覆盖更新上传。

本仓库原发布证书 SHA-256：`f69971cc43c660b73436c539f85a5950a155d3e93dfcb181dc0fe84459a5f8ce`。原签名私钥由维护者私下保存；源码和发布附件不包含密钥或密码。

图片与交易修复测试版可用以下命令构建为精简的独立 Release 包，应用名为“c002apk 独立测试”，不会替换 `com.example.c001apk.replyfix`：

```sh
./gradlew :app:assembleRelease -PtestApplicationId=com.example.c001apk.replyfix.preview -PreleaseVersionName=0.1.1-rc.3-independent -PreleaseVersionCode=251
```

单元测试执行 `:app:testDebugUnitTest :media-support:testDebugUnitTest`。真机调试需要时才构建 `:app:assembleDebug`，不要将其当作发布 APK。

真机回归测试另用 `:app:assembleDebugAndroidTest` 构建，并以相同 `testApplicationId` 参数调用 `:app:connectedDebugAndroidTest`。手机需授权 USB 调试和 USB 安装。

正式维护者应创建自己的密钥并长期保存。可在不提交的 local.properties 中填写 KEYSTORE_PATH、KEYSTORE_PASSWORD、KEY_ALIAS、KEY_PASSWORD。私钥、密码、local.properties 均不得上传 GitHub。

发布 APK 使用本次维护者构建的证书；源码不包含该私钥。自行构建可用自己的签名和独立包安装，无需原签名密钥。

## 产物

脚本把 APK 和 SHA256SUMS 放入 dist/。完整代码、Gradle 文件、Wrapper、内含模块与许可文本属于对应源码；发布时请上传同一 commit 导出的完整源码 ZIP。APK 不包含任何个人首段标识；用户自己的运行数据不属于发布源码的一部分。

## GitHub Actions

正常 Release 工作流调用精简构建脚本；Debug 和无 R8 工作流属于诊断产物。Actions 中的签名步骤需要维护者自行配置已有密钥 Secret；没有配置或签名失败的构建不能作为覆盖更新发布。`docs/build-workflow.yml` 是保留的候选模板，本身不自动运行。
