# HydraPush V1 Finalization

This source tree is the cleaned V1 handoff intended for GitHub and CI.

## Final fixes applied
- Removed first-run demo/sample history seeding. A fresh install creates only today's empty snapshot.
- Dashboard water/push-up logging now returns the inserted row ID so Snackbar Undo deletes exactly the newly logged item.
- Removed in-card Undo controls; Undo is transient through Snackbar.
- Reminders are opt-in by default and Android 13+ permission denial cannot leave a reminder visibly/persistently enabled.
- Reminder alarms are cancelled when notification permission is unavailable.
- Disabling creatine/protein also disables its reminder while preserving all historical records.
- V1 is intentionally light-only to match the approved design; legacy theme values are accepted in backups but restored as LIGHT.
- Debug builds use the standard Android debug keystore. Release signing is optional and environment-driven; no keystore is committed.
- Widget and boot receivers are non-exported; widget quick actions remain internal to the app.
- Calendar day touch targets were expanded to 48dp while retaining the 36dp visual circle.
- Backup import now enforces its 15 MB limit while streaming, validates orphaned rows, duplicate IDs/dates and ranges, and keeps checksum validation.
- Removed AI Studio metadata/artifacts, embedded handoff ZIPs, generated QA screenshots, obsolete Google-services residue, unused sample backup XML, and other template debris.
- Preserved the five approved visual references under `docs/design/`.

## Verification performed in this environment
- All Android XML files parse successfully.
- Kotlin parser pass found no syntax/declaration errors after final edits (Android symbols are unresolved here because the Android SDK is not installed).
- Pure Kotlin `ProgressMath` executable harness passed optional-habit, streak-gap, incomplete-today and 100%-cap checks.
- Static audit found no network libraries or `android.permission.INTERNET` declaration.

## Still required in CI / Android SDK environment
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- inspect merged manifest/APK and prove `android.permission.INTERNET` is absent
- commit the generated Room V1 schema under `app/schemas/`
- install the exact CI-built APK on a device and complete the release checklist

Do not tag V1.0.0 until `docs/V1_RELEASE_CHECKLIST.md` is green.
