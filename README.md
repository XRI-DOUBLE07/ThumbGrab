# ThumbGrab 📱

Native Android app (Kotlin) — YouTube thumbnail downloader with animated splash screen.

## Build APK (no Android Studio needed)

1. Push this repo to GitHub
2. Go to **Actions** tab → the "Build APK" workflow runs automatically
3. When it finishes, download **ThumbGrab-APK** from the artifacts
4. Install `app-debug.apk` on your phone

## Or build locally

Open in Android Studio → Run, or:

```
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Features

- Animated splash screen (logo pop + pulse)
- Paste any YouTube link: watch, youtu.be, Shorts, embed, live — or a bare video ID
- All thumbnail qualities: Max-Res HD, SD 640×480, HQ 480×360, MQ 320×180, preview
- Detects unavailable qualities automatically
- Downloads to `Downloads/ThumbGrab/` via system DownloadManager with notification
