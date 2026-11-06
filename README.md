# Sub2API Mobile

面向 [Wei-Shaw/sub2api](https://github.com/Wei-Shaw/sub2api) 私有部署的原生 Android 客户端。应用不绑定固定站点，首次启动时输入部署地址即可连接。

## 已实现

- 多个自建服务的添加、编辑、删除与切换，自动规范化 `/api/v1`，重启恢复最后使用的服务
- 每个服务独立保存一个账户的登录状态，切换保留登录；退出仅影响当前服务
- 邮箱密码登录、常规输入法输入（密码保持遮罩）、TOTP 两步验证、Refresh Token 自动续期
- 系统 Keystore 加密保存各服务会话，安全存储失败时停止保存并提示错误；会话文件排除系统备份及设备迁移
- 用户概览：余额、并发、今日请求、Token、消费和订阅摘要
- API Key：列表、创建、复制、启停和删除
- 管理员 AI 平台账号：Claude/OpenAI/Gemini/Antigravity/Grok 账号、Cookie/Token、分组、Base URL/API Key 编辑、选模型连接测试、调度启停、优先级显示/修改与剩余额度汇总；未知额度时隐藏账号额度区域，优先级与测试共用操作行
- 桌面小部件：显示当前服务全部 AI 账号的总剩余额度与服务名称，支持手动刷新与每 30 分钟自动刷新
- 渠道状态：管理员与普通用户均可查看整体健康度、可用率、延迟、近期检测记录和各模型 7/15/30 天详情
- 个人用量：今日/近 7 天/近 30 天记录，每页 50 条并支持翻页；模型与时间同行，展示首字耗时、总耗时、TPS、账号/渠道与费用，隐藏分组及密钥，无渠道信息时隐藏渠道行，卡片按首字延迟渐变着色
- 管理员所有用量：全站分页记录、用户/账号/模型筛选、全部历史记录与当前页渠道性能样本
- 管理员用户排行：默认今天，提供今天/昨天/近24小时/近7天/近14天/近30天/本月/上月下拉选择，统一使用服务端聚合，支持按 Tokens、请求数和消费排序
- 订阅：日/周/月额度进度、有效期、兑换码与兑换记录
- 账户：修改用户名、网页控制台、支付入口、文档、退出和切换服务器
- 更新：检查本仓库 `xukgo/sub2api-mobile` 的 GitHub 最新正式版本，支持应用内下载进度、SHA-256 校验与系统安装
- 进度条：连续显示实际比例，不绘制容易被误认为额外进度的尾部终点标记
- 界面：统一浅色与深色主题、排版、导航、表单、卡片和状态信息层级
- Android 13（API 33）及以上，深色模式、手机/平板自适应布局、HTTP 内网部署支持

管理员底部入口为：首页、AI 账号、个人用量、所有用量、我的。管理员的 API 密钥和订阅入口在“我的”中；普通用户不会显示“所有用量”或“AI 账号”。网站用户管理、渠道配置、代理和系统运维仍使用上游 Web 管理后台。

管理员用量与账号接口按官方 Sub2API **v0.2.19** 对接：

- 记录的 TPS 与网页版相同，为输出 Token 数除以完整请求耗时（含首字等待），不使用扣除首字时间后的生成速度。图片等非文本请求或缺少有效耗时时显示 `—`。
- 普通用户接口会隐藏账号名称、渠道等管理员信息。个人用量显示后端获准返回的账号 ID，未提供的字段明确标注；管理员查看自己的用量时使用按自身用户 ID 过滤的管理员接口。个人与全站记录均为每页 50 条。
- “所有用量”的渠道性能汇总仅来自当前页记录，是带样本数的比较数据，不是全站实时网络吞吐。
- 用户排行的全部 8 个时间范围均与官方网页一样直接调用 `/admin/dashboard/user-breakdown`，一次请求返回所选指标的 Top 50；App 不再拉取用量明细自行统计。v0.2.19 与核验时的官方 main 均只接受日期：网页“近24小时”会先减去 24 小时、再截断为日期，通常查询昨日和今日，并非精确滚动 24 小时。App 采用同一口径，选择该项时明确提示，并显示服务端实际起止日期（含结束日期）和时区。接口与 SQL 依据见 [核验记录](docs/research/sub2api-user-ranking.md)。
- 账号卡片开关与网页版一致，控制 `schedulable`（是否参与调度），通过 `/admin/accounts/{id}/schedulable` 保存；优先级可在卡片上点击修改，数值越小越优先。已移除独立的 Cookie/凭据更新和刷新功能。
- 个人与全站用量卡片按首字延迟着色：5 秒以下只在绿色内部渐变，达到 5 秒才进入浅黄；之后按 5 秒浅黄、10 秒橙色、20 秒浅红、40 秒及以上红色进行区间插值。浅色和深色主题分别配置完整色阶，直接使用最终卡片色，缺少有效首字延迟时保持普通底色。
- 编辑账号时原 API Key 不会回显，留空保留已有密钥。修改 Base URL 或输入新密钥时保留其他配置；可用模型通过 `/admin/accounts/{id}/models` 获取，模型测试请求包含 `model_id`。

应用版本：**1.9.0**（versionCode 17）。

登录密码框使用常规 ASCII 输入类型并关闭自动纠错，视觉上继续隐藏密码。应用不会请求密码专用键盘；如果手机厂商仍强制启用安全键盘，需要在手机系统的输入法设置中关闭。

## 多服务使用

首次连接的服务会保存在本机。在登录页点击“切换服务”，或在“我的 → 服务管理”中添加其他服务；每个服务需分别登录，切换后自动恢复该服务的会话。列表中的“已保存登录”表示本机存在令牌，是否仍有效会在联网时确认。

编辑服务名称不会退出登录；修改地址会清除该服务原有凭据并要求重新登录。删除服务仅删除本地配置及凭据，不会删除服务端数据。旧版本已有的单服务登录会在首次启动时自动迁移。

服务之间的请求、刷新令牌及页面状态相互隔离；桌面小部件跟随当前服务，不跨服务合并额度。

## 构建

要求 JDK 17、Android SDK 35。仓库自带 Gradle Wrapper。

Linux / macOS：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --max-workers=4
```

Windows：

```powershell
.\build.bat
```

仅构建 APK：

```powershell
.\build.bat -Task debug
```

输出文件：`app/build/outputs/apk/debug/app-debug.apk`

正式发布前，将 `keystore.properties.example` 复制为本地的 `keystore.properties`，填写发布密钥信息，然后执行：

```powershell
.\build.bat -Task release
```

正式 APK 输出到 `app/build/outputs/apk/release/app-release.apk`。`keystore.properties` 与密钥文件已被 Git 忽略；后续版本必须使用同一份密钥签名，请单独安全备份。

项目目录含中文时，Windows 的 Gradle 测试进程存在类路径问题；构建脚本会自动映射临时英文盘符完成构建并在结束后释放。

## GitHub Actions 自动构建

工作流位于 [`.github/workflows/android.yml`](.github/workflows/android.yml)。提交并推送代码后，分支推送和 Pull Request 都会触发 `Android CI`；也可以在默认分支已有该工作流后，从 Actions 页面点击 **Run workflow** 手动触发。

流程使用 Ubuntu 24.04、JDK 17、Android SDK 35 和仓库中的 Gradle Wrapper。`setup-android` 会显式初始化 SDK 命令行工具、配置 PATH，并安装平台 35 和 Build Tools 34.0.0，不依赖 runner 预先提供 `sdkmanager`。随后运行全部 JVM 单元测试与 Android Lint，成功后编译调试 APK 和设备测试 APK（不运行设备测试）。Gradle 最多使用 4 个 worker，并缓存依赖；同一分支的新提交会取消旧构建。

在仓库 **Actions → Android CI → 对应运行记录 → Artifacts** 下载：

- `sub2api-mobile-debug-<运行编号>-<重试编号>`：包含可安装的 `app-debug.apk` 和配套的 `output-metadata.json` 版本信息。
- `android-reports-<运行编号>-<重试编号>`：包含单元测试与 Lint 报告，检查失败时也会尽量保留已有报告。

产物保留 14 天。当前 CI 不需要配置 Secrets，使用 Android 默认调试签名，不运行真机或模拟器测试，也不自动发布 GitHub Release。不同 CI 运行生成的调试签名可能不同，因此下载的调试 APK 不保证可以相互覆盖安装；需要持续覆盖升级时，应配置固定的正式签名。

## 手动发布 APK 到 Releases

发布工作流位于 [`.github/workflows/publish-apk.yml`](.github/workflows/publish-apk.yml)。普通提交只触发构建；需要发布时，在 GitHub 仓库打开 **Actions → Publish APK to Release → Run workflow**，选择 **main** 分支并点击运行。无需填写构建编号、Artifact 编号或版本号。

流程每次会查询 `main` 最新提交对应的 `Android CI`，选取该提交最近一次运行及其最新重试的 APK 产物。构建尚未成功、产物已过期，或发布途中 `main` 出现新提交时，流程会停止并提示原因，不会回退发布旧提交的包。发布不重新编译：GitHub 服务器直接下载现有 Artifact，并读取其配套版本元数据，生成 `v版本号` 标签、`sub2api-mobile-v版本号.apk` 和 `SHA256SUMS.txt`。标签指向原构建提交；文件上传齐全后才将草稿发布为正式 Release 并设为 Latest。运行页面的 Summary 会给出 Release 链接。

注意：

- 推送本次工作流更新后，先等待最新一次 `Android CI` 成功，再点击发布。更新前的旧 Artifact 缺少版本元数据，不用于此自动选择流程。
- 每次发布新版本前都应增加 `app/build.gradle.kts` 中的 `versionName` 和 `versionCode`。已有版本标签不会被覆盖；重复点击发布同一个版本会停止并提示。
- 网络错误或发布期间出现新提交时，可能留下未发布的草稿；流程不会擅自覆盖已有草稿。可以在 Releases 检查并删除未发布草稿后重试，或增加版本号并重新构建。
- 发布使用内置的 `GITHUB_TOKEN`，权限仅为 `actions: read` 与 `contents: write`，无需个人访问令牌。工作流不会因普通 push 自动发布。
- 产物仍是现有 CI 的调试 APK，转存不会改变其签名，也不会将其转换为 Android release 构建。连续覆盖安装仍需固定签名。
- 私有仓库的 Release 下载仍需要 GitHub 权限；App 没有内置 GitHub 令牌，直接检查更新需要能匿名访问的 Release。Releases 提供直接 APK 链接，但下载速度仍取决于网络。

## 接口基线

数据模型与路由对齐官方 sub2api `v0.2.19`，核心接口为：

- `/api/v1/settings/public`
- `/api/v1/auth/login`、`/auth/login/2fa`、`/auth/refresh`
- `/api/v1/auth/me`、`/user`
- `/api/v1/keys`、`/groups/available`
- `/api/v1/usage`、`/usage/stats`、`/usage/dashboard/stats`
- `/api/v1/admin/usage`、`/admin/usage/stats`、`/admin/dashboard/user-breakdown`
- `/api/v1/subscriptions`、`/subscriptions/progress`
- `/api/v1/redeem`、`/redeem/history`
- `/api/v1/channel-monitors`、`/channel-monitors/{id}/status`
- `/api/v1/admin/accounts`、`/admin/accounts/{id}/test`、`/admin/accounts/{id}/schedulable`
- `/api/v1/admin/accounts/{id}/usage`
- `/api/v1/admin/accounts/{id}/models`；测试接口接收包含 `model_id` 的 JSON
- `/api/v1/admin/accounts/cookie-auth`、`/admin/accounts/setup-token-cookie-auth`（创建账号时使用）
