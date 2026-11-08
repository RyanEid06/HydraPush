# HydraPush

An offline Android tracker for water, push-ups, and pull-ups. Log a drink or a set in seconds, work toward daily goals, and see today's progress from your home screen.

**[Download HydraPush](https://github.com/RyanEid06/HydraPush/releases/latest)** · Android 8.0+ · ARM64

## What you can track

- **Water and exercise** — log quick or custom water amounts, record push-up and pull-up sets, and remove individual entries from today's history.
- **Goals and reminders** — set your daily targets and optionally track creatine and protein.
- **Progress over time** — review streaks, achievements, trends, and a calendar of past activity.
- **Home-screen widget** — see your streak and all three goals side by side.

HydraPush works offline and requests no internet permission. Your data stays on your device. Export or restore a backup from **Settings**.

If you're switching from a private review build, export a backup before uninstalling it. Install the public APK, then import your backup.

## Build from source

Open the project in Android Studio with JDK 17 and Android SDK 36.1. HydraPush uses Kotlin, Jetpack Compose, Room, and DataStore.

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleDebug lintRelease assembleRelease
```

On Windows, use `gradlew.bat` with the same tasks. Debug APKs are written to `app/build/outputs/apk/debug/`. Local release builds are unsigned by default.
