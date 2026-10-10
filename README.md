# PixelSave

Android share target that saves shared images to `Pictures/PixelSave` as original files, with optional SHA-256 verification.

## Usage

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

## Implementation

- Scoped storage on Android 10+, `WRITE_EXTERNAL_STORAGE` only pre-29
- `IS_PENDING=1` during copy so partial files stay hidden from gallery
- Integrity states `VERIFIED / FAILED / SKIPPED`, reported per file
- Stream copy only, no bitmap decode in app process

## Tests

- Unit (`test/`): filename, mime, format, streams, no-bitmap guard
- Instrumented (`androidTest/`): `ImageSaverTest`, share intent round-trip

## Build

Open in Android Studio (compileSdk 36, minSdk 26, Java 17) and Run, or:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
