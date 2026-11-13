# Android standalone application

The Android app runs its downloader on the phone. It does not ask the user for a VideoDL server address and does not call the Flask API.

## Interface

- Material 3 layout with rounded cards and a three-item bottom bar: Home, Downloads, Settings.
- The Downloads page shows the current transfer and persisted completed downloads. Tapping a completed item opens it with an installed player.
- Settings exposes the output folder, local engine description, temporary-cache cleanup and clearing the in-app history without deleting the downloaded media.

## Local engine and real source metadata

- Kotlin + Jetpack Compose provides the interface and writes completed files to Downloads/VideoDL.
- Chaquopy embeds CPython 3.13; yt-dlp extracts metadata and the exact format list returned by the source website.
- The format picker does not invent fixed quality tiers. It displays actual width, height, frame rate, dynamic range, codecs, container and format ID.
- File sizes are never silently presented as exact when only bitrate or approximate source metadata is available. The UI prefixes these values with "约"; a bitrate estimate uses duration and the source-reported bitrate. If it cannot be calculated reliably, the UI shows "大小未知".
- Thumbnail candidates are collected from yt-dlp, sorted by available pixel dimensions, and downloaded into a local image cache with a source Referer and User-Agent fallback. The UI then loads a local validated image file rather than relying on the image host to permit Coil's direct request.
- When video and audio streams are separate, the app performs stream-copy remuxing locally.

## App size

- The distributable build targets arm64-v8a only, avoiding a duplicate x86_64 Python and native-library payload.
- FFmpegKit uses the minimal LGPL ffmpeg-kit-min artifact because the app only remuxes streams.
- The app uses Material Icons Core rather than the full Extended icon catalogue.

## Build

Open clients/android in Android Studio or run:

```bash
python3.13 --version
gradle -p clients/android assembleDebug
```

The build uses Chaquopy 17.2.0 and Python 3.13. GitHub Actions builds the ARM64 APK and reports its exact byte size.

## Current limitations

- yt-dlp support depends on each source website and its current anti-bot behavior. Some sites may require cookies, browser-session data or a JavaScript runtime which is not bundled.
- Some sources do not expose a usable image URL; those videos display a clear cover placeholder instead of a broken image.
- Bitrate-based file sizes are estimates, not guarantees. The downloaded file's final size is authoritative.
- Downloads currently run while the application process is alive. WorkManager-based long-running tasks, automatic resume after process death and cookie import are not included.
- DRM/paid-content access controls are not bypassed.
