# HydraPush V1 Release Checklist

Before tagging V1.0.0:

- [ ] `./gradlew testDebugUnitTest` passes.
- [ ] `./gradlew assembleDebug` passes on a clean checkout.
- [ ] Merged manifest/APK contains no `android.permission.INTERNET`.
- [ ] Fresh install starts with only today's empty record; no sample/demo history.
- [ ] Water/push-up logging and snackbar Undo remove exactly the newly inserted item.
- [ ] Past days remain read-only and missing days reconcile as incomplete.
- [ ] Creatine/protein disable/re-enable preserves historical records and only affects current/future requirements.
- [ ] Notification permission denial leaves reminder toggles OFF.
- [ ] Water interval and daily push-up/supplement reminders fire according to configured schedule.
- [ ] Export/import round-trips database + settings + reminders and rejects corrupt/wrong-app backups.
- [ ] Widget updates and quick actions work after process death and reboot.
- [ ] Dashboard, Stats Overview, Stats Water, Stats Push-ups and Settings are compared against `docs/design/`.
- [ ] Generated Room schema `app/schemas/.../1.json` is committed after the first Android build.
- [ ] Release signing secret is stored outside Git; CI uses `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- [ ] APK SHA-256 and Git commit are recorded for the release.
