# Sub2API Mobile

面向 [Wei-Shaw/sub2api](https://github.com/Wei-Shaw/sub2api) 私有部署的原生 Android 客户端。应用不绑定固定站点，首次启动时输入部署地址即可连接。

## 已实现

- 多个自建服务的添加、编辑、删除与切换，自动规范化 `/api/v1`，重启恢复最后使用的服务
- 每个服务独立保存一个账户的登录状态，切换保留登录；退出仅影响当前服务
- 邮箱密码登录、TOTP 两步验证、Refresh Token 自动续期
- 系统 Keystore 加密保存各服务会话，安全存储失败时停止保存并提示错误；会话文件排除系统备份及设备迁移
- 用户概览：余额、并发、今日请求、Token、消费和订阅摘要
- API Key：列表、创建、复制、启停和删除
- 管理员 AI 平台账号：Claude/OpenAI/Gemini/Antigravity/Grok 账号、Cookie/Token、分组、连接测试、凭据刷新与剩余额度汇总
- 桌面小部件：显示当前服务全部 AI 账号的总剩余额度与服务名称，支持手动刷新与每 30 分钟自动刷新
- 渠道状态：管理员与普通用户均可查看整体健康度、可用率、延迟、近期检测记录和各模型 7/15/30 天详情
- 用量：今日/本周/本月统计与最近请求明细
- 订阅：日/周/月额度进度、有效期、兑换码与兑换记录
- 账户：修改用户名、网页控制台、支付入口、文档、退出和切换服务器
- 更新：自动检查 GitHub 最新正式版本，支持应用内下载进度、SHA-256 校验与系统安装
- 进度条：连续显示实际比例，不绘制容易被误认为额外进度的尾部终点标记
- 界面：统一浅色与深色主题、排版、导航、表单、卡片和状态信息层级
- Android 13（API 33）及以上，深色模式、手机/平板自适应布局、HTTP 内网部署支持

移动端仅加入了管理员常用的 AI 平台账号与 Cookie 管理，以及面向所有用户的只读渠道状态；网站用户、渠道配置、代理和系统运维仍使用上游 Web 管理后台。

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

流程使用 Ubuntu 24.04、JDK 17、Android SDK 35 和仓库中的 Gradle Wrapper，先运行全部 JVM 单元测试与 Android Lint，成功后编译调试 APK。Gradle 最多使用 4 个 worker，并缓存依赖；同一分支的新提交会取消旧构建。

在仓库 **Actions → Android CI → 对应运行记录 → Artifacts** 下载：

- `sub2api-mobile-debug-<运行编号>-<重试编号>`：包含可安装的 `app-debug.apk`。
- `android-reports-<运行编号>-<重试编号>`：包含单元测试与 Lint 报告，检查失败时也会尽量保留已有报告。

产物保留 14 天。当前 CI 不需要配置 Secrets，使用 Android 默认调试签名，不运行真机或模拟器测试，也不自动发布 GitHub Release。不同 CI 运行生成的调试签名可能不同，因此下载的调试 APK 不保证可以相互覆盖安装；需要持续覆盖升级时，应配置固定的正式签名。

## 接口基线

数据模型与路由基于 sub2api `main` 分支，核心接口为：

- `/api/v1/settings/public`
- `/api/v1/auth/login`、`/auth/login/2fa`、`/auth/refresh`
- `/api/v1/auth/me`、`/user`
- `/api/v1/keys`、`/groups/available`
- `/api/v1/usage`、`/usage/stats`、`/usage/dashboard/stats`
- `/api/v1/subscriptions`、`/subscriptions/progress`
- `/api/v1/redeem`、`/redeem/history`
- `/api/v1/channel-monitors`、`/channel-monitors/{id}/status`
- `/api/v1/admin/accounts`、`/admin/accounts/{id}/test`、`/admin/accounts/{id}/refresh`
- `/api/v1/admin/accounts/{id}/usage`
- `/api/v1/admin/accounts/cookie-auth`、`/admin/accounts/{id}/apply-oauth-credentials`
