# HydraPush small-art integration notes

These assets were extracted from the already-approved HydraPush dashboard reference to reduce the gap between the current emoji-heavy implementation and the locked visual direction.

## Files

- `assets/header_runner.png` — athlete + teal motion accents, intended for the upper-right brand header.
- `assets/logo_drop.png` — HydraPush droplet mark reference.
- `assets/water_icon.png` — water-card icon reference.
- `assets/pushup_icon.png` — push-up silhouette reference.
- `assets/creatine_icon.png` — creatine bottle reference.
- `assets/protein_icon.png` — protein shaker reference.
- `android_vectors/ic_hydra_drop.xml` — clean scalable droplet you can use instead of the raster logo drop.
- `android_vectors/ic_notification_hydra.xml` — monochrome notification small icon.

## Usage

Prefer native/vector UI wherever possible. The approved screenshots must remain references, never full-screen raster UI.

Suggested sizes:
- header droplet: 28–32dp
- header athlete: roughly 90–115dp wide on a normal phone; reduce/hide decorative accents on narrow devices
- water/push-up card icons: 28–34dp
- supplement artwork: ~34–42dp

If the extracted PNG edges are not clean enough on the target device, trace/recreate them as Android vectors; keep the same silhouette and palette.

The athlete asset may contain faint motion geometry from the approved art. That is intentional. Keep it translucent enough that it reads as decoration and does not compete with text.
