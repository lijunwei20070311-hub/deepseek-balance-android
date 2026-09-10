# Contributing

Thanks for considering a contribution! Issues and pull requests are welcome.

## Before you start

- This project is an Android app (Kotlin, XML views, Room, WorkManager). Requirements: **JDK 17** and
  **Android SDK platform 35 + build-tools 35**. The Gradle wrapper is committed, so no separate Gradle install
  is needed.
- Read the "Why this app exists" section of the README first — it explains why token usage requires a platform
  session rather than an API key.

## Build and test

```bash
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest    # 26 unit tests, including Robolectric widget rendering
```

Please keep `:app:testDebugUnitTest` green. If you touch the widget layout, run `WidgetRenderTest` — it validates
the layout against real `RemoteViews` constraints (only whitelisted view classes may be used).

## Guidelines

- Keep the platform-usage code (`api/PlatformApi.kt`) tolerant: it parses a private, undocumented API, so prefer
  defensive parsing plus a visible error over a silent failure.
- Never log or transmit credentials. API key and platform session must stay in `DsbPrefs` (encrypted storage).
- Unit tests for new pure logic are strongly encouraged; the existing tests are plain JUnit 4 (+ Robolectric where
  Android resources are involved).
- Match the existing code style (Kotlin official style, 4-space indentation, Chinese UI strings in
  `res/values/strings.xml`).

## Reporting bugs

Include: app version (or commit), Android/One UI version, what you did, what you expected, what happened, and any
error text shown in the app (the app surfaces API failures on screen and in the widget on purpose).
For widget issues, a screenshot plus the launcher name helps a lot.

## Legal / ethical note

This app reads your own DeepSeek account data using your own credentials. Contributions that scrape other
people's accounts, bypass rate limits, or bundle credentials will be rejected.
