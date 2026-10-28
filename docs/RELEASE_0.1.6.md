**维护与新增功能：[tiancaiProgramer](https://github.com/tiancaiProgramer)**

本版修复图文详情显示，并防止发布后排版丢失时误清空原稿。

- 图文详情按服务端内容块顺序显示，修复内容块标识问题，兼容图文标题字段。
- 发布后自动读回，核对正文、图片顺序、图片身份及图片说明；核验通过才清除本机草稿。
- 酷安会将部分过短图文转换为普通动态，造成图片集中到末尾及说明丢失。本版会提前说明，检测到转换或无法确认完整时明确提示、保留原稿及图片，并阻止当前会话重复提交。此修复不会强制服务器保留短文的图文排版。
- 保留既有登录、评论、图片与 Live 图、二手交易、平板布局及 GitHub 更新功能。

版本 **0.1.6**，versionCode **278**，正常包名 `com.example.c001apk.replyfix`，沿用原发布签名，启用 R8 和资源精简，可保留数据覆盖升级。

58 项单元测试通过。MIX Fold 3 / Android 16 实测正常图文的私密上传、创建、混排和说明读回；同时验证短文转换提示及重启后原稿完整恢复。测试内容已删除。发布验证码、跨账号可见性及所有设备组合未纳入本轮验收，详见附件中的验证记录。

附件包含正式 APK、同一提交的完整源码 ZIP、SHA256SUMS、VALIDATION.json，以及构建与图文验证说明。应用继续从本仓库检查更新；可在“设置 → 关于 → 检查 c002apk 更新”手动检查，后台通知按既有周期执行。

来源与致谢：[原项目 bggRGjQaUbCoE](https://github.com/bggRGjQaUbCoE/c001apk-compose)、[维护版 frisk1127](https://github.com/frisk1127/c001apk-compose)。保留原作者、AGPL-3.0 及第三方许可，详见 [AUTHORS.md](https://github.com/tiancaiProgramer/c002apk/blob/main/AUTHORS.md)、[NOTICE](https://github.com/tiancaiProgramer/c002apk/blob/main/NOTICE)、[LICENSE](https://github.com/tiancaiProgramer/c002apk/blob/main/LICENSE)。
