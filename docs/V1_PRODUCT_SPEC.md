# HydraPush V1 — Locked Product Spec

HydraPush is an Android-only, offline-first personal tracker. Package/application ID: `com.hydrapush.tracker`.

## Permanent daily metrics
- Water: adjustable daily goal; log +150, +250, +500 ml or custom amount. Only water counts.
- Push-ups: adjustable daily goal; log individual sets. Standard push-ups only.

## Optional daily habits
- Creatine: once-per-day done/not-done.
- Protein shake: once-per-day done/not-done.
- Each can be independently disabled and re-enabled. Disabling hides it from current/future active tracking and reminders without deleting history or recalculating old days.

## Streak
A perfect day requires water goal + push-up goal + every optional habit that was required on that historical day. Past days are read-only. Missing days are recorded as incomplete when the app next reconciles; history is never fabricated.

## Screens
- Dashboard: dominant Water and Push-ups cards; optional supplement cards; streak and daily completion.
- Stats: Overview plus dedicated Water and Push-ups drilldowns, calendar/history, records, consistency and achievements.
- Settings: goals, active optional habits, per-activity reminders, export/import backup, About/version.

## Design
V1 intentionally uses the approved light sporty mint/teal/lime/coral HydraPush theme. Dark mode is not part of V1. Locked references are in `docs/design/`.

## Data/privacy
- Local Room database + DataStore only.
- No account, analytics, cloud, network stack or `INTERNET` permission.
- Manual JSON export/import with validation/checksum.
- App updates must preserve the application ID, signing identity and database data.
