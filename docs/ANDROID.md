# Android standalone application

VideoDL is an Android-native downloader. The app runs yt-dlp locally on the device and does not ask users for a VideoDL server address or call the Flask API.

## Native interface

- The complete Android interface is Kotlin + Jetpack Compose + Material 3. The app no longer uses a WebView or an HTML/CSS/JavaScript frontend.
- The three destinations are Download, Files and Settings, using Material 3 bottom navigation and native Compose controls.
- The download screen supports pasted or shared links, clipboard paste, source metadata and thumbnail preview, and the actual format list returned by yt-dlp.
- Video and audio formats can be selected independently. The format rows show the source-reported resolution, frame rate, dynamic range, codecs, container, format ID and file size.
- Loading, empty, error, selected-format and download-progress states are native Compose UI states. Completed items remain in the local history and can be opened with an installed Android player.
- Settings exposes the output folder, temporary-cache cleanup and clearing the in-app history without deleting saved media.

## Local engine and real source metadata

- Chaquopy embeds CPython 3.13; yt-dlp extracts metadata and the exact format list returned by the source website.
- The format picker does not invent fixed quality tiers. It displays actual width, height, frame rate, dynamic range, codecs, container and format ID.
- File sizes use only source-reported filesize or filesize_approx values. Approximate source values are prefixed with "约". If the source does not report a file size, the app shows "大小未知"; bitrate is never converted into a guessed file size.
- Thumbnail candidates are collected from yt-dlp, sorted by available pixel dimensions, downloaded into a local image cache with a source Referer and User-Agent fallback, then validated and downsampled. Compose displays the resulting local image.
- When video and audio streams are separate, the app performs stream-copy remuxing locally.

## App size

- The distributable build targets arm64-v8a only, avoiding a duplicate x86_64 Python and native-library payload.
- FFmpegKit uses the minimal LGPL ffmpeg-kit-min artifact because the app only remuxes streams.

## Build

Open `clients/android` in Android Studio or run:

~~~bash
python3.13 --version
gradle -p clients/android assembleDebug
~~~

The build uses Kotlin 2.0.21, the Kotlin Compose compiler plugin, Jetpack Compose with the Material 3 BOM, Chaquopy 17.2.0 and Python 3.13. The debug APK is written to `clients/android/app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations

- yt-dlp support depends on each source website and its current anti-bot behavior. Some sites may require cookies, browser-session data or a JavaScript runtime which is not bundled.
- Some sources do not expose a usable image URL; those videos display a cover placeholder instead.
- File sizes are shown only when supplied by the source. The downloaded file's final size is authoritative.
- Downloads currently run while the application process is alive. WorkManager-based long-running tasks, automatic resume after process death and cookie import are not included.
- DRM/paid-content access controls are not bypassed.
