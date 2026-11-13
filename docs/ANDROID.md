# Android standalone application

The Android app runs its downloader on the phone. It does **not** ask the user for a VideoDL server address and does not call the Flask API.

## Local engine

- Kotlin + Jetpack Compose provides the interface and writes completed files to Downloads/VideoDL.
- Chaquopy embeds CPython 3.13.
- yt-dlp extracts metadata and the exact format list returned by the source website.
- The format picker is generated from real source formats. It does not invent fixed 720p/1080p/4K choices. It displays actual width, height, frame rate, dynamic-range label (when present), codecs, container, and format ID.
- File size is marked as approximate when yt-dlp only provides filesize_approx; otherwise the UI shows the exact source-reported size or "大小未知".
- When a chosen video and audio stream are separate, the app performs stream-copy remuxing locally. It does not encode or transcode the streams.
- The app accepts plain URLs, text containing one URL, Android share intents and opened HTTP(S) links.

## App size

- The distributable Android build targets arm64-v8a only, avoiding a duplicate x86_64 Python and native-library payload. This build is intended for current ARM64 Android phones, not x86_64 emulators.
- FFmpegKit uses the minimal LGPL ffmpeg-kit-min artifact because VideoDL only needs MP4/MKV stream-copy muxing. It does not bundle the optional audio/video encoder libraries from the full package.
- The app no longer includes the Material Icons Extended catalogue or debug UI tooling.

The APK is smaller by removing unused native ABIs and optional FFmpeg modules, rather than by removing supported source resolutions or download formats. The final artifact size is reported by the matching GitHub Actions run.

## Network and privacy

The Android app needs ordinary internet access to reach the source website. This is not a connection to a VideoDL server. No server deployment, API URL, account or remote queue is required.

## Build

Open clients/android in Android Studio or run:

~~~bash
python3.13 --version
gradle -p clients/android assembleDebug
~~~

The Android build uses Chaquopy 17.2.0 and Python 3.13. The GitHub Actions Android job installs Python 3.13 before invoking Gradle.

## Current limitations

- yt-dlp support depends on each source website and its current anti-bot behavior. Some sites may require cookies, browser-session data, a JavaScript runtime or browser TLS impersonation which is not bundled.
- High-resolution formats may be separate video and audio streams; the app downloads both and remuxes them locally with FFmpegKit.
- Downloads currently run while the application process is alive. WorkManager-based long-running tasks, automatic resume after process death, cookie import and DRM/paid-content bypass are not included.
- The source site may not provide every resolution or audio format. VideoDL can only display formats which the source actually exposes to yt-dlp.
