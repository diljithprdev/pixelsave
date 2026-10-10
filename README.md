# PixelSave — save shared images as original files

Android share-target that saves images to `Pictures/PixelSave` without recompression. Share from any app, get the original file + SHA-256 integrity check.

## Use

1. Share an image (or several) from any app → choose PixelSave
2. Files land in `Pictures/PixelSave`, counter + notification confirm
3. Settings: toggle SHA-256 verify, toggle notification, reset counter

## Structure

```
app/src/main/java/com/pixelsave/app/
├── MainActivity.kt        share intent handling, progress, counter UI
├── SettingsActivity.kt    verify / notify toggles
├── save/ImageSaver.kt     MediaStore copy (Q+ scoped storage + legacy fallback),
│                          IS_PENDING, SHA-256 source vs saved compare, coroutines IO
├── notify/Notifications.kt  completion notification, channel setup
└── util/                  FileNameUtils, MimeTypes, Streams, Prefs, ShareIntents, Format
```

## Details

- Scoped storage on Android 10+, `WRITE_EXTERNAL_STORAGE` only pre-29
- `IS_PENDING=1` during copy so half-written files never show in gallery
- Integrity states: `VERIFIED / FAILED / SKIPPED`, surfaced per file
- No bitmap decode in app process — stream copy only (enforced by test)

## Tests

- Unit (`test/`): filename, mime, format, streams, no-bitmap guard
- Instrumented (`androidTest/`): `ImageSaverTest`, share intent round-trip

## Build

Open in Android Studio (compileSdk 36, minSdk 26, Java 17) and Run, or:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
