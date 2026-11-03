# HydraPush

HydraPush V1 is a native Android daily water, push-up and optional supplement tracker built with Kotlin, Jetpack Compose, Room and DataStore. It is intentionally **100% offline**.

## Build

Requirements: Android Studio / Android SDK 36.1, JDK 17+ compatible with the configured Android Gradle Plugin.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug builds use Android's standard generated debug keystore. Do not commit a debug or release keystore. For a signed release, provide these environment variables:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The permanent application ID is `com.hydrapush.tracker`. Changing it creates a different app and breaks update continuity.

## Privacy

HydraPush has no `INTERNET` permission and no Firebase, Gemini, Retrofit, OkHttp, analytics or cloud dependencies. User data remains on-device unless the user explicitly exports a backup file.

See `docs/V1_PRODUCT_SPEC.md` and `docs/V1_RELEASE_CHECKLIST.md`.
