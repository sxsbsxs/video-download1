# VideoDL 架构说明

## 产品边界

VideoDL 以 Android 原生下载应用为主。**Android 客户端直接在手机本地运行 yt-dlp，不依赖 Flask API、远程任务服务或用户填写的服务器地址。** Flask 服务端仍保留为可选组件，供其他语言客户端或自建集成使用。

## Android 本地数据流

```text
Android UI (Kotlin + Compose)
       |
       v
LocalYtDlpEngine.kt
       |
       +--> Chaquopy / CPython 3.13
       |        |
       |        v
       |     yt-dlp
       |        |
       |        +--> source metadata + actual formats
       |        |
       |        +--> local stream downloads
       |
       +--> FFmpegKit (merge separate audio/video streams)
       |
       v
MediaStore / Downloads/VideoDL
```

Android:
- Parses share text and extracts one source URL locally.
- Calls yt-dlp directly in an embedded Python runtime.
- Lists all video and audio formats returned by the source, instead of a fixed four-choice quality menu.
- Downloads source streams into app cache and merges separate streams on-device.
- Saves the completed artifact into Android's Downloads collection.
- Stores a small local recent-download list in app preferences.

The Android app does not use `/api/info`, `/api/tasks`, or the remote task owner token.

## Optional server components

The Flask backend remains available for non-Android clients and automation:

```text
HTTP clients
    |
    v
videodl/api.py
    |--- URL validation ------ videodl/security.py
    |--- quality rules ------- videodl/quality.py
    |--- request limiting ---- videodl/rate_limit.py
    |
    +---- sync compatibility endpoint
    |
    +---- asynchronous task routes
                 |
                 v
         videodl/tasks.py
         bounded ThreadPoolExecutor
                 |
                 v
         videodl/engine.py
         yt-dlp + FFmpeg
                 |
                 v
        per-task temporary folder
```

The task store, queue, rate limiter and API contracts describe this optional server path; they are not part of the Android standalone download flow.

## Other client layers

- C11: stable C ABI over libcurl, bounded JSON response, streaming transfer, progress callback and temp-file rename.
- C++: higher-level client which reuses the C SDK.
- Rust: platform-independent state/progress/quality/filename primitives.
- Java, Swift and C#: additional platform clients with feature limits documented in `clients/README.md`.

## Security boundary

yt-dlp only downloads media exposed by the source website to the local app. VideoDL does not implement DRM circumvention, paywall bypass or access-control bypass. The optional server has a separate SSRF threat model: it validates public HTTP(S) URLs, but production deployment must also enforce outbound network restrictions at the container/host level. See SECURITY.md.
