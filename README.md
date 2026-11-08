# HydraPush

HydraPush is an offline Android tracker for water, push-ups, pull-ups, and optional creatine and protein habits. It uses Kotlin, Jetpack Compose, Room, and DataStore.

Download the signed ARM64 APK from [GitHub Releases](https://github.com/RyanEid06/HydraPush/releases). If you use a private review build, export a backup in Settings before uninstalling it. Install the public APK and import the backup. The public APK uses a dedicated release signing key.

## Features

- Water logging with quick amounts and custom entries, plus push-up and pull-up logging by set.
- Independent optional supplement habits, goals, and reminders.
- Current and historical statistics, streaks, achievements, and a read-only calendar.
- A summary-only home-screen widget showing the streak and water/push-up progress. Tapping it opens the app.
- User-initiated local backup export/import with validation and atomic restore.
- Timestamped dashboard histories for today's water, push-up, and pull-up entries. Remove individual entries from each history. Past records remain read-only in normal use.

Stats exclude an unfinished current day from a rate or average until that day’s relevant goal is met. The screen explains this eligibility rule. Today’s requirements reflect current goals and enabled habits; older snapshots retain the requirements that applied to them.

Live entries and imported backups share one set of limits: water entries 1–5,000 ml, push-up sets 1–1,000 reps, water goals 1–20,000 ml, and push-up goals 1–10,000 reps.

## Build and verification

Use Android Studio with the project’s Android Gradle Plugin and JDK 17 or newer. Run the CI checks locally with the Gradle wrapper.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug lintRelease assembleRelease
```

The release build is ARM64-only and uses application ID `com.hydrapush.tracker`. Local release builds are unsigned by default.

## Privacy

The app has no `INTERNET` permission, network stack, analytics, Firebase, cloud sync, or advertising. Data stays on-device unless the user exports a backup file.

See [product specification](docs/V1_PRODUCT_SPEC.md), [release checklist](docs/V1_RELEASE_CHECKLIST.md), and [finalization notes](docs/V1_FINALIZATION.md).
