# HydraPush — final audit / fix pass

Use this after the existing HydraPush project. Do **not** redesign the product. The approved light aqua/lime/coral direction and package id `com.hydrapush.tracker` stay locked.

## Executive result

The current implementation is a strong structural pass, but it is **not yet a verified 95%+ visual match and not release-ready**. Source-level visual fidelity is roughly **89–91%** before real-device screenshot comparison. The product/data concept is good; the remaining gap is specific and fixable.

### What is already good
- Dashboard information architecture follows the approved mockup: Water + Push-ups dominate side-by-side, optional supplements below, streak + daily completion below.
- Stats correctly split Overview / Water / Push-ups instead of duplicating large average cards on Overview.
- Settings includes required Active Habits controls; Water and Push-ups are permanently active and supplements can be disabled independently.
- Historical daily snapshots preserve goals/required flags; water and push-up totals are event/set based.
- Pure Kotlin perfect-day / current streak / longest streak / optional-habit logic passed an independent harness.
- `applicationId` is still correct.

## P0 — fix before claiming a good debug build

### 1. Remove the AI/network stack completely
The app is supposed to be offline-only, but the current Gradle project still includes Firebase AI, Firebase App Check, Google Services, secrets plugin, Retrofit, OkHttp, logging interceptor, Moshi/codegen, Firebase BOM, and `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` metadata. None are used by HydraPush.

Remove from build/plugins/catalog:
- Firebase BOM / Firebase AI / Firebase App Check / Firebase auth/firestore references
- `com.google.gms.google-services`
- secrets Gradle plugin and `.env` Gemini placeholder
- Retrofit / converter-moshi / OkHttp / logging-interceptor
- Moshi and Moshi KSP codegen if nothing else uses it
- unused camera/location/credential dependencies from the catalog if they remain only as template baggage

Keep the minimal native stack: Compose, Material 3, core/activity/lifecycle, Room + Room KSP, DataStore, coroutines, tests.

Change `metadata.json` so there is **no Gemini/server capability**. After building, inspect the **merged manifest/APK**, not only source manifest, and prove `android.permission.INTERNET` is absent.

### 2. Gradle wrapper / portable build
Current ZIP has `gradle-wrapper.properties` but is missing `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar`. Generate the official wrapper and commit those files. AGP 9.1.1 + Gradle 9.3.1 are a valid pairing; do not churn versions unless the actual environment requires it.

Run and record:
```bash
./gradlew --version
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

### 3. Debug/release signing
The build script points debug signing at `${rootDir}/debug.keystore`, but that file is not in the exported repo. Do not commit a personal release keystore.

For a portable GitHub repo:
- use Android's normal default debug signing or a CI-created ephemeral debug key;
- use one permanent private release key later and keep it outside Git;
- preserve the same release key forever so updates install over old data.

### 4. Backup is incomplete
`BackupManager` currently serializes only goals, active supplements and theme from settings. It **drops all reminder settings**, which violates the product spec.

Backup/restore must include:
- waterReminderEnabled
- waterReminderIntervalHours (and any start/end/fixed schedule fields you introduce)
- pushUpReminderEnabled + time
- creatineReminderEnabled + time
- proteinReminderEnabled + time
- appearance setting if it remains exposed
- all daily snapshots, water events, push-up sets, config revisions and achievement state

Add a unit test where non-default reminder settings round-trip exactly.

### 5. Backup validation / atomic restore
Current parser does not validate ISO dates/duplicate IDs/duplicate snapshot days/file size thoroughly, and invalid settings can fail only **after** Room has already been replaced. That can cause a partial restore.

Before replacing anything:
- impose a sane maximum backup size (for example 10–25 MB);
- parse and fully validate every date, ID, goal, reminder interval/time and uniqueness constraint;
- reject malformed/duplicate/future-schema data without writing;
- keep old DB/settings in memory or a temporary copy so any unexpected post-validation failure can roll back;
- only report success after Room + DataStore + reminders + widget are all refreshed.

### 6. Water reminder interval is currently wrong
UI says “Every N hours,” but `ReminderScheduler.scheduleAll()` always schedules water at 10:00 **once per day**. Implement the actual configured interval. Push-up/creatine/protein time pickers must also be genuinely configurable, not only a few canned presets if the UI claims custom reminders.

Request notification permission only when the user is enabling a reminder, not when disabling one.

Schedule existing enabled reminders on first app initialization as well as after settings changes and boot/timezone changes.

### 7. BroadcastReceiver coroutine lifetime
`BootCompletedReceiver` and `HydraPushAppWidgetProvider` launch coroutines and immediately return from the receiver. Android may kill the process before the work completes. Use `goAsync()` and finish the pending result, or move durable work to WorkManager/another appropriate mechanism.

## P1 — required for correctness / product contract

### 8. Historical goal line in Stats
The Water/Push-up chart draws one flat goal line using the last day’s goal. This is wrong when the user changed goals during the selected period. Render the historical goal for **each day** (stepped/segmented line or per-day marker) from that day’s frozen snapshot.

### 9. “Weekly Consistency” calculation/label
In a 30D/90D/All view, the current drilldown card calculates consistency over the whole selected range but labels it “Weekly Consistency.” Either:
- compute a real last-7-day/current-week value, or
- rename it to “Range Consistency.”

The “best full week” calculation should clearly state whether it is all-time or constrained to the selected range. Prefer selected-range consistency for range-sensitive metrics, with an explicit all-time record only where labeled.

### 10. Today in completion-rate denominators
The product contract prefers excluding an unfinished current day from final completion percentages so morning use does not punish the user. Keep today visible as “in progress,” but use elapsed completed days for summary completion rates unless explicitly labeled live/provisional.

### 11. Achievement false unlock after Undo
Achievements are persisted as permanently unlocked as soon as a qualifying entry is added, but `undoWater()` / `removeSet()` do not re-evaluate/remove a just-earned false unlock. If the user accidentally logs 3 L or 100 reps then immediately undoes it, the badge can remain unlocked.

Fix by deriving unlocked status from actual history and persisting celebration/first-unlock state carefully, or delete an unlock created today if the current history no longer qualifies and no prior day qualified.

### 12. Widget contract
The widget currently covers Water + Push-ups only and logs a hard-coded +10 push-ups. Improve reliability and alignment:
- use `goAsync()` or WorkManager for widget mutations;
- keep +250 Water quick action;
- for Push-ups either open the Add Set flow or make the +10 quick set explicitly labeled/configurable;
- if medium space allows, show active supplement state only when those habits are active;
- remove unused manifest actions for supplement toggles unless they are actually implemented;
- refresh after day rollover, import, goal changes and supplement activation changes.

### 13. Normal notification icon
Do not use the full-color launcher vector as `setSmallIcon()`. Android notification small icons must be a proper monochrome glyph. This kit includes `android_vectors/ic_notification_hydra.xml`.

### 14. Theme control is currently misleading
Most screens hard-code light colors/white surfaces, so “Follow System / Dark” is not a real dark theme. The original decision says light is the priority and the control may be hidden if dark is not truly implemented.

For V1 choose one:
- **Preferred:** force the approved light HydraPush theme and hide Appearance/Theme for now; or
- fully theme every hard-coded surface/text/nav/chart/dialog before leaving Follow System visible.

Do not ship a half-dark UI.

## P1 — visual fidelity to get from ~90% to 95%+

### 15. Stop using emoji as production icons
The current UI uses 💧, 🥛, ↗, ⚡, 🥤, 🏆, 🔥, etc. Emoji rendering changes by Android/device and looks noticeably less like the approved mockups.

Use Material/vector/custom assets. This kit contains small extracted references from the approved mockup:
- `assets/logo_drop.png`
- `assets/header_runner.png`
- `assets/water_icon.png`
- `assets/pushup_icon.png`
- `assets/creatine_icon.png`
- `assets/protein_icon.png`

Use these as direct assets only if their alpha edges look clean on-device; otherwise recreate vectors from them. Do **not** embed whole screenshots as backgrounds.

### 16. Header fidelity
Replace the current circular emoji droplet and simple Canvas runner. Match the approved header:
- real outlined aqua HydraPush drop mark at ~28–32dp;
- “Hydra” dark green + “Push” teal;
- translucent athlete at top-right with teal speed accents;
- athlete never covers title/date or clips badly on 360dp.

### 17. Card background accents
Add the very subtle aqua/lime flowing accent shapes seen in the approved cards using Compose `Canvas`/gradients. Keep them low-contrast and decorative only.

### 18. Undo without breaking the approved card silhouette
Permanent “Undo last” rows inside the two primary cards change their height and can make the side-by-side cards uneven. Prefer a Snackbar after logging (`Added 250 ml · Undo`, `Added 20 reps · Undo`) and/or reserve equal invisible height if an in-card undo is retained.

### 19. Tap targets / accessibility
Several buttons are 38–42dp and calendar/range cells are ~36dp. Bring interactive targets to at least 44dp (48dp preferred) without visually bloating them. Test 360dp width and 1.3x font scale. At 320dp, reduce the water ring or stack the primary cards rather than clipping.

### 20. Final screenshot QA is mandatory
Source review cannot certify 95% pixel/visual fidelity. After all P0/P1 fixes:
1. install a real debug APK;
2. seed deterministic sample data matching the mockup values;
3. capture Dashboard, Stats Overview, Water, Push-ups, Settings at the same phone width;
4. compare side-by-side with approved PNGs;
5. fix typography, spacing, card radii, icon size, shadow, nav capsule, chart proportions;
6. repeat until there are no obvious visual deviations.

## P2 polish
- Replace About copy “past records permanently preserved” with “past records are read-only during normal use; full backup restore replaces the dataset after confirmation.”
- Add test coverage for first launch reminder scheduling, denied notification permission, disable/re-enable supplement reminders, backup reminder round-trip, invalid backup rollback, widget quick action and historical changing-goal charts.
- Consider Room migration tests before any V1.1 schema change.

## Required evidence before declaring V1 done
Do not claim success from source inspection. Provide:
- successful Gradle commands;
- unit/instrumented test results;
- merged manifest/APK permission proof (no INTERNET);
- emulator/device screenshots for all five approved visual states;
- backup round-trip evidence with non-default reminders;
- upgrade test using same package + signing identity with existing local data.
