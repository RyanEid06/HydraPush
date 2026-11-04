# HydraPush V1 Repair Verification

## Scope and decisions

This pass repairs the existing V1 app while retaining its package identity, Room history, backup format, offline behavior, palette, and navigation. The manifest remains offline-only with no `INTERNET` permission. The owner’s latest widget direction supersedes the original four-action-card requirement: the visible widget is a compact header and water/push-up progress summary. Header and progress cards open the app; there are no visible quick-action or supplement controls. Existing receiver actions remain for compatibility and prior instrumentation only.

## Changes

- **Dashboard and shared layout:** use Scaffold/system insets and scroll padding; balance the water/push-up and optional supplement cards; keep push-up set history summarized without hiding additional sets; refresh the authoritative current date on resume and periodically; add correction/removal for today’s water and push-up entries; consolidate rapid Undo feedback.
- **Stats and history:** preserve Stats selections and scroll state across bottom-navigation changes; repair chart labels, empty-history presentation, drilldown summary, and calendar day details/navigation. Calendar supports six-row months and future days without making future dates editable.
- **Settings and reminders:** configure reminder intervals/times while reminders are off, refresh permission state on return from Android Settings, show clear backup errors, and display the app version dynamically. Scheduling checks notification permission, cancels alarms when notifications cannot be shown, avoids unnecessary rescheduling, and suppresses reminders for completed or disabled habits. When notification permission is denied, imported reminder preferences are disabled so the app does not present unavailable reminders as active.
- **Brand and launcher:** align the header wordmark, droplet, subtitle, and runner; replace raster habit marks with vectors; provide adaptive launcher foreground/background and monochrome resources.
- **Widget:** retain live water/push-up totals and progress, place a number and fire at the top right, remove the runner and action-card row, and let the visible panel wrap its content rather than stretching across empty launcher space.

## Observed verification

- Clean debug and release builds, debug and release lint, and all 28 unit tests passed on the compact widget build (`build/final-compact-build.log`).
- All five instrumentation tests passed at 360dp with font scale 1.3 (`build/final-compact-instrumentation-retest.log`). The project-local Pixel 6 AVD ran Android API 37, Google APIs, x86_64, on serial `emulator-5556`.
- Manual emulator review covered Dashboard quick water amounts (+150/+250/+500), custom 330 ml, a 15-rep set and Undo; Stats Overview at 7D/30D/90D/All, Water ranges, Push-ups 7D/All; current six-row November calendar, completed and future dates, prior-month navigation and day details; and Settings permission denial/grant and Android Settings return. Final backup flows are recorded below.
- A synthetic 120-day backup was imported and SAF export/import succeeded in the earlier QA session. A summary-widget build was added through the launcher and inspected at 360dp/font scale 1.3; its cards opened the app. Earlier widget action broadcasts updated shared totals, but the current product surface intentionally no longer exposes those actions.
- Screenshots from the manual pass are under `build/qa/final-*` (intermediate pre-runner-removal build, not the final widget capture).

## Final delivered build

- `build/final-widget-fit-build.log`: debug build, ARM64 release build, debug/release lint, instrumentation APK build and all 28 unit tests succeeded after the final layout edit. A clean build also passed earlier in the same repair pass. Lint reports zero errors and 51 warnings per variant (dependency recommendations, hardcoded strings, small text, unused legacy colors, and other advisories).
- Exact signed release installed on the Pixel 6 API 37 emulator (ARM64 translation supported). All five instrumentation tests passed at approximately 412dp/default font (`build/final-delivery-instrumentation.log`) and 360dp/font 1.3 (`build/final-delivery-instrumentation-narrow.log`). No app code changed afterward.
- Final manual checks: widget opens the app; app water logging updates its total; changing water goal 2500 to 3000 changes the displayed percentage from 83% to 69%, then restoring the goal restores 83%; final SAF export/import reports success; Dashboard, Stats and Settings open; widget totals survive an emulator reboot and the app opens afterward. The emulator displayed its previously observed System UI boot ANR; choosing Wait restored the launcher. This was not a HydraPush crash. The pre-reboot app crash buffer was empty.
- Final widget screenshots: [normal width](../build/qa/final-widget.png), [360dp/font 1.3](../build/qa/final-widget-narrow.png), and [after reboot](../build/qa/final-widget-after-reboot.png). Compared with the approved cropped reference, this retains the droplet, wordmark and paired progress cards, removes the action row and runner, and replaces the wordy streak badge with number + fire per the owner's latest instructions. No clipped widget text was observed in these configurations.
- Desktop APK: `C:/Users/Administrator/Desktop/HydraPush-1.0.0-final-arm64.apk` (11,717,174 bytes). Workspace copy: `build/delivery-final/HydraPush-1.0.0-arm64.apk`.
- SHA-256: `36763b5255c989213259d38267126a62dfe97d5a95c849a61b7697a5a9626df3`. Desktop copy and installed emulator base APK match this hash.
- Package `com.hydrapush.tracker`, version 1.0.0/code 1, min SDK 26, target SDK 36, ARM64 only, non-debuggable. No INTERNET permission. Signature v2/v3 and 16 KB ZIP alignment verify. Existing private test certificate SHA-256: `f362708af345c607f91700a453893cf0c50731a60b38fcc13be85d0e579e3c58`. This remains a private test-signed release build, not a public production signing identity.
- All 75 non-signature APK entries match the unsigned release. Evidence: `build/delivery-final/{signature.txt,badging.txt,permissions.txt,content-verification.json,source-provenance.json}`.
- The owner subsequently authorized committing and pushing the cumulative repair to `main` on 2026-11-04. This commit records the verified source based on `1e22b78`; source hashes match the tested APK inputs. GitHub CI runs automatically on push; its result is separate from the local verification above. No tag or public release is authorized. The provided reference image remains local and preserved.

## Limits

No physical phone or OEM launcher acceptance was performed. Actual timed reminder delivery, OEM battery restrictions, and a real midnight rollover were not directly observed. There are no visible widget quick-action buttons in the approved summary-only design.
