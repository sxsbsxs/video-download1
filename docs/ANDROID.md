# Android standalone application

The Android app runs its downloader on the phone. It does **not** ask the user for a VideoDL server address and does not call the Flask API.

## Local engine

- Kotlin + Jetpack Compose provides the interface and writes completed files to `Downloads/VideoDL`.
- Chaquopy embeds CPython 3.13 in the APK.
- yt-dlp 2026.10.26 extracts video metadata and the actual format list from the source website.
- The UI presents the formats returned by the source: resolution, frame rate, container, codecs and estimated size. Audio-only formats are listed separately.
- When the chosen video and audio streams are separate, FFmpegKit merges them locally on the device.
- The app accepts plain URLs, text containing one URL, Android share intents and opened HTTP(S) links.

The Android app needs ordinary internet access to reach the source website. This is **not** a connection to a VideoDL server. No server deployment, API URL, account or remote queue is required.

## Build

Open `clients/android` in Android Studio or run:

```bash
python3.13 --version
gradle -p clients/android assembleDebug
```

The Android build uses Chaquopy 17.2.0 and Python 3.13. The GitHub Actions Android job installs Python 3.13 before invoking Gradle.

## Current limitations

- yt-dlp support depends on each source website and its current anti-bot behavior. Some sites may require cookies, browser-session data, a JavaScript runtime or browser TLS impersonation which is not bundled.
- High-resolution formats may be separate video and audio streams; the app downloads both and merges them locally with FFmpegKit.
- Downloads currently run while the application process is alive. WorkManager-based long-running tasks, automatic resume after process death, cookie import and DRM/paid-content bypass are not included.
- The source site may not provide every resolution or audio format. VideoDL can only display formats which the source actually exposes to yt-dlp.
