# Sub2API Mobile 数据去向与隐私静态审查

审查日期：2026-11-06（UTC）。

> 本报告审查的是上游原始 v1.6.1 快照。当前工作区后续的多服务改造已取消明文凭据存储降级，并补齐新旧会话文件的备份排除；其他结论仍需结合当前代码判断。

## 版本与范围

- 上游：https://github.com/startuip/sub2api-mobile
- 最新正式发布：[v1.6.1](https://github.com/startuip/sub2api-mobile/releases/tag/v1.6.1)，发布时间 2026-07-27 11:11:13 UTC。
- 审查提交：`6d2cc2d4a119128643d9d959055639991214d8e3`。审查时 `main` 与 `v1.6.1` 指向同一提交。
- 检查范围：应用源码、直接依赖声明、Manifest、备份规则、构建脚本，以及 GitHub 发布元数据。重点追踪网络请求的目的地址、请求数据、凭据存储和后台任务。
- CodeGraph 未建立本项目索引，使用原生文件读取及有界搜索完成审查。
- 未安装或运行应用，未进行真机抓包、发布 APK 反编译、源码与 APK 一致性验证、传递依赖或服务端安全审计。未执行项目构建和测试。以下是静态审查结果，不等于对发布二进制的安全认证。

## 结论

在审查的应用源码和直接依赖声明中，未发现隐藏的作者数据收集服务器、统计埋点 SDK、广告 SDK，或将业务账号、Cookie、API Key 复制上传到独立第三方的路径。

但不能认定“没有任何向第三方发送数据的风险”：应用启动时会自动请求 GitHub；加密存储失败后的明文会话文件未被备份规则排除，可能经系统备份出设备；允许使用 HTTP 也会暴露传输中的凭据。

## 已确认的数据去向

| 接收方 | 触发方式 | 数据及用途 | 代码证据 |
| --- | --- | --- | --- |
| 用户配置的 Sub2API 后端 | 配置服务器、登录及业务操作 | 登录邮箱和密码、两步验证码、访问令牌、刷新令牌、兑换码、资料和账号管理请求；请求携带语言，部分用量请求携带时区 | `data/ApiClientFactory.kt:25,43,71`；`data/ApiRepository.kt:26,32,38,60`；`data/ApiService.kt:15` |
| 同一 Sub2API 后端的管理员接口 | 用户新增、修改或兑换 AI 平台凭据 | 用户输入的 AI 平台 API Key、Cookie/session key、access/refresh token 等；属于后台账号管理功能 | `MainViewModel.kt:514,562`；`data/ApiRepository.kt:108,163,172,178`；`data/ApiService.kt:110,139,145` |
| `api.github.com` | 主界面 ViewModel 初始化时自动检查，也支持手动检查 | `GET /repos/startuip/sub2api-mobile/releases/latest`。独立客户端不读取 SessionStore，没有添加业务 Authorization 或账号请求体；GitHub 仍可看到出口 IP、访问时间和 HTTP 请求信息 | `MainViewModel.kt:104,120`；`data/AppUpdateRepository.kt:32,44,255` |
| GitHub 发布资产及下载重定向的服务器 | 用户点击下载更新 | 下载 APK；显式请求头包括 Accept 与 `Sub2API-Mobile/<待下载版本>` User-Agent，没有添加业务令牌 | `ui/components/AppUpdateDialog.kt:77`；`data/AppUpdateRepository.kt:89,109` |
| 用户点击的网页 | 点击网页版、注册、找回密码、支付、文档或发布页 | 通过系统 URI handler 打开链接；文档 URL 来自所配后端。未发现显式在这些链接中拼接业务令牌 | `ui/Sub2ApiApp.kt:110,113,135`；`ui/screens/AuthScreens.kt:184`；`ui/screens/ProfileScreen.kt:109` |
| 系统云备份或设备迁移目标（有条件） | 加密初始化失败、产生普通会话文件，并满足系统备份或迁移条件 | 未加密的服务器地址、access token、refresh token、过期时间，见发现 1 | `data/SessionStore.kt:64,71`；`AndroidManifest.xml:9`；备份 XML |

表中 Kotlin 路径相对 `app/src/main/java/com/sub2api/mobile/`；Manifest 相对 `app/src/main/`。

桌面小部件在启用后安排每 30 分钟刷新，使用同一个业务仓库访问已配置的 Sub2API 后端，未发现单独的上报地址。证据：`widget/QuotaWidgetProvider.kt:9`、`widget/QuotaWidgetWorker.kt:33,111`。

## 发现 1：明文降级会话文件漏入备份范围

风险：较高；需要触发加密存储失败，并满足系统备份或迁移条件。

证据链：

1. `SessionStore.kt:71` 默认使用 EncryptedSharedPreferences。
2. `SessionStore.kt:82` 捕获任意 Exception 后，静默改用普通 SharedPreferences：`sub2api_session`。保存文件为 `sub2api_session.xml`。
3. `SessionStore.kt:44` 写入访问令牌和刷新令牌；普通模式下这些字段未加密。
4. `app/src/main/AndroidManifest.xml:9` 开启 `allowBackup=true`。
5. `app/src/main/res/xml/backup_rules.xml:3` 仅排除了加密会话文件 `sub2api_secure.xml`、小部件文件和更新目录。
6. `app/src/main/res/xml/data_extraction_rules.xml:3` 的云备份与设备迁移规则同样漏掉 `sub2api_session.xml`。

Android 官方说明：自动备份默认包含几乎所有应用文件；只写 exclude 不会把其他未列出的文件排除。因此该普通会话文件仍在备份范围内。实际是否上传、上传到哪个备份服务，取决于设备、用户设置和备份条件，不能据此断言每台设备已经发生上传，也没有证据说明作者能读取该备份。

官方依据：[Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup)，已通过 Context7 核验旧版 full-backup-content 和 Android 12+ data-extraction-rules 的默认行为。

建议：凭据加密失败时停止持久化并明确报错；至少在两套规则的所有相关分支排除 `sub2api_session.xml`，并处理历史明文文件。普通文件属于应用私有目录，并非任意普通应用都能直接读取，但这不解决备份问题。

## 发现 2：允许明文 HTTP 传输凭据

风险：较高；取决于服务器配置是否使用 HTTP。

- `SessionStore.kt:99` 允许显式 `http://`；不填写 scheme 时默认 HTTPS。
- `app/src/main/AndroidManifest.xml:17` 全局设置 `usesCleartextTraffic=true`。
- `ApiRepository.kt:26` 将密码放入登录请求；`ApiClientFactory.kt:52` 添加 Bearer token；管理员接口也会提交平台凭据。

如果配置 HTTP，这些内容可以被链路上的攻击者读取或修改。源码未发现自定义“信任所有证书”或关闭主机名校验的实现，但正常 HTTPS 行为不能保护用户主动选择的明文 HTTP。

建议：默认强制 HTTPS；确需 HTTP 内网调试时进行明确提示并限定范围，避免在不可信网络使用管理员账号。

## 发现 3：启动时自动访问 GitHub，更新下载缺少目的地限制

风险：低，主要是第三方访问元数据和更新渠道信任问题；未发现业务凭据外传证据。

- `MainViewModel.kt:104` 在初始化时自动检查更新，即使尚未配置业务服务器或登录，也可能发起 GitHub 请求。
- `AppUpdateRepository.kt:83` 直接采用 GitHub API 返回的 `browser_download_url`；`:37` 显式允许重定向；`:109` 下载前未做 HTTPS 或目标域名白名单检查。
- 当前 v1.6.1 发布元数据中的 APK 地址为 `https://github.com/startuip/sub2api-mobile/releases/download/v1.6.1/sub2api-mobile-v1.6.1.apk`。没有发现当前资产指向陌生站点。
- APK 资产大小为 8,712,011 字节，GitHub 元数据中的 SHA-256 为 `da990e7fca596704c43d017eeb8240048ff0eb1c08d96b67781c38cc84b93fec`；这是 API 元数据，未通过下载二进制独立验证。
- `AppUpdateRepository.kt:150` 仅在元数据提供有效 digest 时比较 SHA-256；digest 缺失或无效时仍允许完成下载。它不是独立的发布者身份验证。
- 下载由用户点击触发，安装交给系统安装器；源码未发现静默安装路径。

建议：自建版本改用自己的更新源或默认手动检查；限制 HTTPS 与预期发布/CDN 来源，保留必要的合法 CDN 重定向；要求有效摘要，并遵守 Android 更新签名验证。现有 SHA-256 来自同一发布渠道，不能证明 APK 与开源源码一致。

## 其他观察

- 直接运行时依赖为 AndroidX、Compose、WorkManager、Retrofit、OkHttp、Gson、security-crypto，未声明 Firebase Analytics、Crashlytics、Sentry、Bugly、友盟等收集 SDK。证据：`app/build.gradle.kts:65`。未对传递依赖和构建下载的二进制逐一审计。
- Manifest 声明联网、网络状态与安装 APK 权限，没有声明通讯录、定位、麦克风、相机、短信等权限。没有发现读取设备广告 ID、通讯录、定位、任意剪贴板内容的调用。密钥页仅在用户点击时将 API Key 写入剪贴板。
- `ApiClientFactory.kt:38,59` 在业务客户端启用 BASIC HTTP 日志，可能将请求 URL、状态等写入本机日志。未发现该日志主动上传的代码；BASIC 不是请求头或正文级日志。
- 管理员 Cookie/API Key 提交给所配置后端是明确功能。这个后端能收到相应明文凭据，即使客户端没有隐藏上报，也必须信任后端运营者。客户端源码无法证明后端不再转发这些数据。
- 构建依赖源为 Google Maven、Maven Central、Gradle Plugin Portal 和 Gradle 官方分发地址；这些是构建阶段联网，与运行时业务凭据请求不同。未验证 Gradle Wrapper JAR 的官方校验值。

## 采用建议

可以参考其功能和原生 Android 架构；涉及真实管理员凭据前，应先修复明文降级与备份遗漏、收紧 HTTP，并明确更新检查的数据去向。若要求业务数据只接触自己的服务器，应使用自己的构建签名和更新渠道，对最终 APK 做抓包与源码一致性检查。

本次只获取源码并添加本审查记录，未修改应用实现。
