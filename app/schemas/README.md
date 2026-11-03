# Room schema history

`TrackerDatabase` has `exportSchema = true` and KSP writes schemas into this directory.

On the first verified Android/CI build, commit the generated `1.json` schema before any database version bump. Never hand-write or fabricate Room schemas. Future database migrations must retain every generated schema revision.
