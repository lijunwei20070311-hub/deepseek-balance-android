# DeepSeek Balance for Android

[简体中文](README.md) · **English**

A lightweight Android app that shows your **DeepSeek account balance** and the **tokens you have used across your whole account** (today / this month / all time, with a per-model breakdown), plus a **home screen widget** for your Samsung (One UI) or stock launcher.

```
┌──────────────────────────────────────────┐
│ DeepSeek 余额用量             12:30 更新 ⟳│
│                                          │
│ 余额             今日  3.2万 tokens       │
│ ¥98.65           输入 2.8万 · 输出 4,120  │
│ CNY · 可用       本月  125万 tokens       │
└──────────────────────────────────────────┘
```

## Why this app exists

DeepSeek's **public** API exposes a balance endpoint only — `GET https://api.deepseek.com/user/balance`.
There is **no public API for historical token usage**, so an API key alone cannot tell you how many tokens you burned today.

The numbers shown on the [DeepSeek Platform](https://platform.deepseek.com) dashboard come from the platform's
web-frontend endpoints, which authenticate with a **signed-in platform session** (`userToken`), not with an API key.
This app signs in to that same session inside an in-app WebView, so the usage it shows **matches the platform
dashboard exactly** and includes calls you made anywhere (official site, other tools, your own scripts) — not just
calls made from this app.

| Data | Endpoint | Auth |
| --- | --- | --- |
| Balance, granted/topped-up split | `api.deepseek.com/user/balance` (public, documented) | API key (`sk-...`) |
| Tokens (today / month / per model), requests, cost | `platform.deepseek.com/api/v0/usage/amount`, `usage/cost`, `usage/export` (private web endpoints) | Platform session (`userToken`) |

> ⚠️ **Caveat** — the usage endpoints are the platform's private frontend API. They are not officially documented and
> may change without notice. When they fail, the app surfaces the error on screen instead of pretending the data is fine.

## Features

- **Balance**: total / granted / topped-up, availability state, last-sync time; pull-to-refresh.
- **Usage (platform-accurate)**: today, this month and all-time tokens; request counts; spend; input
  (cache-hit / cache-miss) and output token split; per-model ranking for the current month; a 14-day bar chart.
- **Home screen widget**: balance + today's tokens (with input/output split) + month tokens + sync time,
  tap ⟳ to refresh instantly, tap the card to open the app.
- **Refresh**: WorkManager periodic sync (15 / 30 / 60 / 120 / 360 minutes, 15 min is the Android floor) plus a
  30-minute widget fallback.
- **Optional chat probe**: send one message to verify your API key and see the server-reported `usage`.
- **No manual data entry**: everything comes from the platform.

## Install

Grab the latest APK from the [Releases page](../../releases/latest)
(`DeepSeekBalance-vX.Y.Z-debug.apk`) and install it on your phone
(allow "install from unknown sources" the first time).

Alternatively build it yourself (see below) — the output is
`app/build/outputs/apk/debug/app-debug.apk`.

The released APK is **debug-signed**, which is fine for personal use. For a release build, open the project in
Android Studio and generate a signed release APK.

### First run

1. **Open the app once** — Android only lists an app's widgets after it has been launched at least once.
2. Tap **登录平台账号 / Sign in to platform** and log in inside the WebView (SMS / QR / password).
   The app automatically captures `localStorage.userToken` and validates it.
3. Enter your **API key** and tap save to fetch the balance.

### Samsung widget

Long-press an empty area of the home screen → **Widgets** → find **余额用量** → drag it out (4×2 is a good size).

If One UI's power saving delays background sync: Settings → Battery → Background usage limits → add this app to
**Never sleeping apps** (or Settings → Apps → this app → Battery → **Unrestricted**). Tapping ⟳ always refreshes
immediately, regardless of background limits.

## Privacy and security

- The API key and the platform session are stored in `EncryptedSharedPreferences` (AES-256-GCM / AES-256-SIV, key
  material in the Android Keystore). They are never logged and never sent anywhere except
  `api.deepseek.com` and `platform.deepseek.com`.
- HTTPS only (`usesCleartextTraffic=false`); cloud backup is disabled so credentials never leave the device.
- Sign out at any time: Settings → sign out of the platform account / clear the API key.
- The platform session is equivalent to your web login — treat it like a password.

## Build from source

Requirements: JDK 17, Android SDK (platform 35, build-tools 35), Gradle (the wrapper is included).

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

On Windows you can also just run `build-apk.bat`.

Run the tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Project layout

```
app/src/main/java/com/dsh/deepseekbalance/
├── api/     DeepSeekClient (balance / chat), PlatformClient + PlatformApi (platform usage)
├── data/    Room: local ledger + platform usage cache; Repository as the single data source
├── prefs/   DsbPrefs — encrypted credentials and settings
├── sync/    SyncWorker (WorkManager periodic sync)
├── ui/      dashboard, onboarding, platform WebView login, settings, chart view
└── widget/  app widget provider, renderer, boot receiver
```

Tech: Kotlin, AndroidX + Material 3, Room, WorkManager, coroutines, RemoteViews widget, XML views
(minSdk 26, targetSdk 35).

## Tests

`./gradlew :app:testDebugUnitTest` runs 26 unit tests:

- `ApiParsingTest` — balance and chat `usage` parsing
- `PlatformApiTest` — platform usage parsing (per-model / per-type totals, per-day rows, cost, export CSV, expired session)
- `LedgerMathTest` — balance-delta → token estimation
- `FmtTest` — number formatting
- `WidgetRenderTest` — **Robolectric** rendering of the widget layout with real `RemoteViews` constraints.
  This one caught a real production bug: a `<View>` divider is not allowed inside `RemoteViews`, which made the
  widget render as a blank card on a real phone. Keep it green.

## Scope

- Android only (this is an Android client; there is no desktop or iOS version here).
- Not affiliated with DeepSeek. No warranty — use at your own risk.
- The app is read-only with respect to your account: it queries balance/usage and can send a chat probe when you tap it.

## License

[MIT](LICENSE)
