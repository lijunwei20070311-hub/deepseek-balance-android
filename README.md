# DeepSeek 余额 · 用量（安卓端）

**简体中文** · [English](README.en.md)

一个轻量安卓 App：登录 DeepSeek 账号后实时查看**账户余额**与**账号下全部 tokens 用量**（今日 / 本月 / 累计 / 分模型 / 消费），并在**桌面小组件**（三星 One UI 桌面支持）上直接显示余额与用量。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2026%2B-brightgreen.svg)](#六自己编译)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen.svg)](../../releases/latest)

```
#此项目由deep seek-v4-pro开发及内容编写#
```

---

## 一、数据获取

| 数据 | 来源 | 认证方式 |
| --- | --- | --- |
| 账户余额、赠送/充值余额 | 官方公开接口 `GET https://api.deepseek.com/user/balance` | **API Key**（`sk-...`） |
| **全部 tokens 用量**（今日/本月/累计/分模型/消费） | 开放平台后台接口 `platform.deepseek.com/api/v0/usage/amount`、`usage/cost`、`usage/export` | **平台账号登录态**（`userToken`） |

关键点：**API Key 只能查余额，查不到 tokens 用量**——这是官方公开接口的限制（[官方文档](https://api-docs.deepseek.com/api/get-user-balance/) 只有余额接口）。
用量数据在平台网页后台里，App 通过内置网页登录拿到登录态后读取，所以：

- 显示的数字与 **platform.deepseek.com「用量信息」页面完全一致**；
- 包含你在**任何地方**调用产生的用量，不是只统计本 App 的调用；
- 不需要手动录入任何数据。

> 说明：这两个用量接口是平台网页版的私有接口，不是官方公开 API；DeepSeek 若调整接口，App 可能需要跟着更新。
> 取不到数据时界面会直接显示失败原因，不会假装成功。

> 登录态只保存在你手机本地的加密存储中，App 不会把它发送到除 platform.deepseek.com 以外的任何地方。

## 二、功能

| 页面 | 能力 |
| --- | --- |
| 首次配置 | ① 登录平台账号（内置网页登录，短信/扫码/密码均可）→ 读取官方用量；② 填 API Key → 读取余额 |
| 仪表盘 | 余额、赠送/充值、可用状态；今日 tokens（输入 / 输出 / 缓存命中 / 未命中拆分）、今日请求数、今日消费；本月同上；累计 tokens 与累计消费；近 14 天柱状图；本月分模型用量排行 |
| 设置 | 后台刷新间隔（15/30/60/120/360 分钟）、币种、价格（仅估算用）、余额告警阈值、平台账号退出、清除 API Key、小组件添加说明 |
| 调用测试 | 可选：直接发一次请求并显示服务端返回的精确 usage，顺便验证 Key 是否可用 |
| 桌面小组件 | 余额 + 今日 tokens（含输入/输出拆分）+ 本月 tokens + 更新时间；右上角 ⟳ 立即刷新；整卡点击进 App |
<img width="968" height="2376" alt="Screenshot_20260910_213011" src="https://github.com/user-attachments/assets/eaadda2a-99e1-4fa4-9033-82d437f52bc0" />


## 三、安装

**方式一（推荐）**：到 [Releases](../../releases/latest) 页面下载最新 APK（`DeepSeekBalance-vX.Y.Z-debug.apk`）。

**方式二**：自己编译（见第六节），产出 `app/build/outputs/apk/debug/app-debug.apk`。

安装步骤：

1. 把 APK 传到手机（微信/QQ/数据线）点击安装，首次需允许「安装未知来源应用」；
2. **先打开一次 App**（很重要：安卓要求 App 启动过一次后，桌面小组件才会出现在小组件列表里）；
3. 进入 App → 点「登录平台账号」→ 在网页里登录 → 返回即为已连接；
4. 再填 API Key → 「保存并验证」，余额就出来了。

> APK 为 debug 签名，自用安装没问题；如需正式签名，用 Android Studio 打开工程生成 release 包。

## 四、三星 小组件

1. **确保 App 已经打开过至少一次**，并且已登录；
2. 回到桌面 → **长按桌面空白处** → 「**小组件**」→ 找到「**余额用量**」→ 拖到桌面（建议 4×2，可拉伸）；
3. 若卡片显示「未登录平台」/「点 ⟳ 刷新」，点卡片右上角 ⟳ 立即同步。
<img width="968" height="1984" alt="Screenshot_20260910_213212_One UI Home" src="https://github.com/user-attachments/assets/580c4a2d-2a27-428c-a281-7eb057c45951" />

小组件上的信息：余额（副标题是币种/状态）、今日 tokens（下面是输入/输出拆分）、本月 tokens（同样带拆分）、右上角为最近同步时间。

### 刷新机制与省电

- WorkManager 周期同步，系统下限 15 分钟；小组件自身还有 30 分钟的兜底刷新；
- 三星 One UI 的省电模式 / 休眠应用会延迟后台刷新，要保持准时：
  - 设置 → 电池 → 后台使用限制 → 把「余额用量」加入**不受限制**（或从「休眠应用」中移除）；
  - 设置 → 应用 → 余额用量 → 电池 → **不受限制**；
- 点小组件上的 ⟳ 是立即刷新，不受后台限制影响。

## 五、安全与隐私

- API Key 与平台登录态都存放在 `EncryptedSharedPreferences`（AES-256-GCM/SIV，密钥由系统 Keystore 保护），不写日志、不上传第三方；
- 网络请求只发往 `api.deepseek.com`（余额）与 `platform.deepseek.com`（用量），强制 HTTPS，已关闭明文流量；
- 已关闭系统备份，凭据与数据不会进云备份；
- 退出登录：设置 → 「退出平台账号」/「清除 API Key」。

> 平台登录态等同于你的网页登录会话，请勿把手机或登录态交给他人。

## 六、编译

要求：JDK 17 + Android SDK（platform 35、build-tools 35）+ Gradle（工程自带 wrapper）。

```bat
build-apk.bat                 :: Windows 一键构建
:: 或
gradlew.bat :app:assembleDebug
```

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

用 Android Studio 打开工程目录也可以直接 Run。

## 七、已做的自动化验证

| 项目 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过（AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / minSdk 26） |
| 余额与 chat usage 解析测试 | 通过 |
| **平台用量解析测试**（`PlatformApiTest`） | 月度分模型/分类型汇总、按天数据、消费金额、导出 CSV、登录态失效识别 |
| **小组件渲染测试**（`WidgetRenderTest`，Robolectric 真实解析布局） | 布局可解析、控件内容正确写入、未登录 / 登录过期 / 窄卡片 / 异常状态都有可见文案 |
| 数字格式化测试（`FmtTest`） | 通过 |
| 应用图标 | 使用用户提供的图片（968×968）生成全密度方形/圆形/自适应图标；已从 APK 内解出图标与原图逐像素比对，平均差 0.52/255（即同一张图） |

> 未做真机点击测试（开发环境没有可用安卓设备/模拟器）。若真机上有异常，界面会把失败原因显示出来，把截图发我即可继续修。

## 八、常见问题

**Q：小组件加到桌面后一片空白 / 什么都不显示？**
A：先确认 App 打开过一次并完成登录；然后点小组件右上角 ⟳。新版本渲染失败时会把原因直接写在卡片上（例如「组件出错：xxx」），不会再空白。

**Q：小组件列表里找不到「余额用量」？**
A：安卓要求 App 至少启动过一次；部分启动器还会隐藏「已休眠应用」。先打开一次 App，再从小组件面板里找。

**Q：tokens 显示 0 / 没数据？**
A：说明平台账号没连上。进 App → 点「登录平台账号」；若提示登录态失效，重新登录即可。

**Q：数字和平台网页后台一样吗？**
A：一样。App 用的就是后台页面那批接口，按 UTC 切天（与后台一致）。页面若按北京时间显示「今日」，则在凌晨 0–8 点之间会与 UTC 口径略有差异。

**Q：只想看余额，不想登录平台账号行不行？**
A：可以。只填 API Key 时仍显示余额；tokens 一栏会提示需要登录平台账号才能读取官方用量。

**Q：余额提示 401？**
A：检查 Key 是否完整、是否在平台被禁用；Base URL 保持 `https://api.deepseek.com`。

## 九、工程结构

```
app/src/main/java/com/dsh/deepseekbalance/
├── api/        DeepSeekClient（余额/对话）、PlatformClient + PlatformApi（平台官方用量）
├── data/       Room：本地账本 + 平台用量缓存；Repository 统一取数
├── prefs/      DsbPrefs（加密存储 API Key / 平台登录态 + 设置）
├── sync/       SyncWorker（WorkManager 周期同步）
├── ui/         仪表盘、首次配置、平台网页登录、设置、调用测试、自绘柱状图
└── widget/     小组件 Provider / 渲染 / 开机注册
```
