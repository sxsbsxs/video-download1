# Android standalone application

The Android app runs yt-dlp on the phone. It does not ask the user for a VideoDL server address and does not call the Flask API.

## Interface

- The interface is built from standalone HTML, CSS and JavaScript assets and rendered in Android WebView.
- Kotlin is limited to Android platform integration: the JavaScript bridge, clipboard access, MediaStore saving, opening completed files and local engine orchestration.
- The UI has three distinct destinations: Home, Downloads and Settings. It uses a restrained Google-inspired visual system with responsive spacing, rounded surfaces, an adaptive bottom navigation bar, loading/empty/error states and progress feedback.
- The Downloads page shows active progress and persisted completed downloads. Tapping a completed item opens it with an installed player.
- Settings exposes the output folder, local engine description, temporary-cache cleanup and clearing the in-app history without deleting downloaded media.

## Local engine and real source metadata

- Chaquopy embeds CPython 3.13; yt-dlp extracts metadata and the exact format list returned by the source website.
- The format picker does not invent fixed quality tiers. It displays actual width, height, frame rate, dynamic range, codecs, container and format ID.
- File sizes use only source-reported filesize or filesize_approx values. Approximate source values are prefixed with "约". If either a separate video or audio stream has no source size, the combined file size is shown as "大小未知"; bitrate is never converted into a guessed file size.
- Thumbnail candidates are collected from yt-dlp, sorted by available pixel dimensions, and downloaded into a local image cache with a source Referer and User-Agent fallback. The app validates and downsamples the image, re-encodes it as a local JPEG, and loads that file in WebView.
- When video and audio streams are separate, the app performs stream-copy remuxing locally.

## App size

- The distributable build targets arm64-v8a only, avoiding a duplicate x86_64 Python and native-library payload.
- FFmpegKit uses the minimal LGPL ffmpeg-kit-min artifact because the app only remuxes streams.

## Build

Open clients/android in Android Studio or run:

~~~bash
python3.13 --version
gradle -p clients/android assembleDebug
~~~

The build uses Chaquopy 17.2.0 and Python 3.13. GitHub Actions builds the ARM64 APK and reports its exact byte size.

## Current limitations

- yt-dlp support depends on each source website and its current anti-bot behavior. Some sites may require cookies, browser-session data or a JavaScript runtime which is not bundled.
- Some sources do not expose a usable image URL; those videos display a clear cover placeholder instead of a broken image.
- File sizes are shown only when supplied by the source. The downloaded file's final size is authoritative.
- Downloads currently run while the application process is alive. WorkManager-based long-running tasks, automatic resume after process death and cookie import are not included.
- DRM/paid-content access controls are not bypassed.
