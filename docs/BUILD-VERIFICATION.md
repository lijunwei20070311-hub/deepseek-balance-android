# 交付说明（v2 · 构建与验证记录）

## 交付物

| 路径 | 说明 |
| --- | --- |
| `D:\DSH\DeepSeekBalance\` | 完整可编译的安卓工程（Kotlin + Room + WorkManager + 桌面小组件） |
| `D:\DSH\DeepSeekBalance\dist\DeepSeekBalance-debug.apk` | 已编译的 debug 安装包（8.05 MB，SHA256 `5278427B…57CED`） |
| `D:\DSH\DeepSeekBalance\README.md` | 中文使用说明（安装、三星小组件、刷新机制、常见问题） |
| `D:\DSH\DeepSeekBalance\build-apk.bat` | Windows 一键编译脚本 |

## v2 的两处关键修改

### 1. 修复「小组件加到桌面后一片空白」

**根因**：`res/layout/widget_usage.xml` 里的分隔线用了 `<View>` 标签。
桌面加载小组件走的是 `RemoteViews` 的受限 LayoutInflater，只允许白名单控件（FrameLayout / LinearLayout / RelativeLayout / TextView / ImageView / Button / ProgressBar 等），
`android.view.View` 不在白名单里 → 抛 `InflateException: Class not allowed to be inflated android.view.View` → 整个卡片渲染失败 → **一张空白卡**。

**修复**：把 `<View>` 换成 `FrameLayout`（同样 1dp 宽 + 背景色，视觉一致）。
**防回归**：新增 `WidgetRenderTest`（Robolectric，7 个用例），在 JVM 里真实解析 `widget_usage.xml` 并执行 RemoteViews 的每个 setter —— 布局里再出现非法控件、id 写错、内容没写进去，测试都会直接失败。

顺带加固：
- `WidgetUpdater` 渲染包了 try/catch，**出错时把原因直接画在卡片上**（例如「组件出错：xxx」），不再静默空白；
- 补上标题写入、窄卡片自适应（隐藏次要行）、小组件自带 30 分钟兜底刷新。

### 2. tokens 用量改为「开放平台官方全量数据」，去掉手动录入

**为什么**：官方公开 API（`/user/balance`）只有余额，没有用量；要拿「账号下**全部** tokens 用量」，必须用开放平台网页后台的私有接口，而它认的是**平台账号登录态**，不是 API Key。

**实现**：
- 新增 `PlatformClient` + `PlatformApi`：`GET platform.deepseek.com/api/v0/usage/amount`、`usage/cost`（本月，含分天/分模型/分类型）、`usage/export`（近 14 天按天导出，zip 内 CSV）；
- 新增 `PlatformLoginActivity`：**内置网页登录** platform.deepseek.com，登录后自动从 `localStorage.userToken` 取出登录态并校验入库（也保留手动粘贴兜底）；
- 仪表盘改为展示官方口径：今日 / 本月 / 累计 tokens、请求数、消费金额、缓存命中/未命中/输出拆分、本月分模型排行；
- **移除「录入用量」页面**，不再需要任何手工登记；
- 余额差额估算降级为「未登录平台账号时的参考值」，且与官方数据分开存储、不会混算。

## 功能对照

| 需求 | 实现 |
| --- | --- |
| 登录账号 | ① API Key（`GET /user/balance` 校验，读余额）② 平台账号网页登录（读官方全量用量）；两者都存 EncryptedSharedPreferences（AES-256-GCM/SIV） |
| 实时查看剩余余额 | 总余额、赠送/充值、可用状态、更新时间；下拉刷新与刷新按钮即时拉取 |
| 全部 tokens 使用量 | 平台官方接口口径：今日/本月/累计、请求数、消费、输入（缓存命中/未命中）/输出拆分、本月分模型、近 14 天柱状图 |
| 三星桌面小组件 | 余额 + 今日 tokens（含输入/输出拆分）+ 本月 tokens + 同步时间；⟳ 立即刷新；整卡进入 App；WorkManager 周期同步（15/30/60/120/360 分钟）+ 30 分钟兜底 |

## 验证记录

| 项目 | 命令/手段 | 结果 |
| --- | --- | --- |
| 编译 | `gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| 单元测试 | `gradlew :app:testDebugUnitTest` | **26 个用例全部通过**：`ApiParsingTest` 4、`PlatformApiTest` 6、`LedgerMathTest` 6、`FmtTest` 3、`WidgetRenderTest` 7 |
| 小组件渲染 | Robolectric 真实解析布局 + `RemoteViews.apply()` | 通过（修复前该测试会以 `Class not allowed to be inflated` 失败，正是真机白屏的同一原因） |
| 平台用量解析 | 按网页接口真实结构构造样例 | 月度分模型/分类型汇总、按天、消费金额、CSV 导出、多币种择优、登录态失效（40002/40003）识别 |
| APK 签名 | `apksigner verify` | 通过（v2 方案，Android Debug 证书） |
| APK 组件与布局 | `aapt2 dump` | 小组件布局仅含 LinearLayout/TextView/ImageView/FrameLayout（**已无非法 `<View>`**）；`UsageWidgetProvider`、`WIDGET_REFRESH`、`appwidget.provider` 元数据、`PlatformLoginActivity`、`BootReceiver` 均就位 |
| 接口连通性 | `curl` 实测 `api.deepseek.com` | 正常返回结构化 401/429（路径与鉴权格式正确） |
| 平台用量接口连通性 | `curl` 实测 `platform.deepseek.com/api/v0/usage/*` | 返回 429（本机出口 IP 被平台限流），**未能在开发环境连通验证**，需在手机上实际登录后确认 |
| 真机安装与点击 | — | **未执行**（本机无可用安卓设备；模拟器需 CPU 硬件虚拟化，本机不支持） |

产物指纹：

```
文件：D:\DSH\DeepSeekBalance\dist\DeepSeekBalance-debug.apk
大小：8.05 MB
SHA256：5278427B2F7A8249D1C622BF4C96600110010189DBB334DF405A325717357CED
```

## 3. 应用图标已替换为用户提供的图片

原图：`C:\Users\PC\Downloads\Image_1789038324122_752.jpg`（968×968 JPEG，暖白底 ≈ `#F4F3F1` + 中央蓝色图形）。

生成方式（脚本 `E:\ai-toolchain\imgtool\make-icons.js`，sharp 处理，**图片内容不做任何裁切**）：

| 产物 | 说明 |
| --- | --- |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` | 48/72/96/144/192 px，整张图直接缩放 |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png` | 整张图做圆形遮罩（圆形桌面用） |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png` | 108/162/216/324/432 px 自适应前景，图片内容缩进 66% 安全区居中、四周补同色底，避免被启动器裁切 |
| `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 自适应图标：背景 `@color/ic_launcher_background`（`#F4F3F1`）+ 前景层 |

旧的矢量图标（`drawable/ic_launcher*.xml`）已删除，避免与新的 mipmap 资源重名冲突。

**图标验证**：从构建产物 APK 里解出 `ic_launcher.png` 与原图逐像素比对 —— 平均差 0.52/255、最大差 8（JPEG 压缩误差范围内），即 **APK 里的图标就是这张图本身**；自适应前景的边角与中心像素也与原图一致。

## 装机后请重点确认两件事

1. **小组件是否正常显示**（不再空白）——这是本次修复的核心；
2. **登录平台账号后，今日/本月 tokens 是否与 platform.deepseek.com「用量信息」页面一致**。
   平台用量接口是网页私有接口，若数据取不到，App 会把失败原因显示在仪表盘和小组件上（例如「登录状态已失效」或接口报错），把截图发我即可继续修。

## 复现构建所需工具链（本次使用）

| 组件 | 位置 |
| --- | --- |
| JDK 17.0.20.1 (Temurin) | `E:\ai-toolchain\jdk\jdk-17.0.20.1+1` |
| Android SDK（platform 35 / build-tools 35.0.0 / platform-tools） | `E:\ai-toolchain\sdk` |
| Gradle 8.11.1 | `E:\ai-toolchain\gradle\gradle-8.11.1` |
| 构建脚本 | `E:\ai-toolchain\build.ps1` |
| 依赖缓存 | `E:\ai-toolchain\gradle-home` |
