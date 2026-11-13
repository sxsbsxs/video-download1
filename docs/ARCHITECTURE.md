# VideoDL 架构说明

## 产品边界

VideoDL 是 Android 优先的多平台应用，不开发网站。Python Flask 只提供 JSON / 文件流 API，yt-dlp 负责站点提取，FFmpeg 负责媒体后处理。

## 后端分层

~~~text
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
~~~

### Module responsibilities

- app_factory.py: creates Flask app and wires dependencies.
- api.py: HTTP route contracts and HTTP status mapping.
- config.py: validated environment configuration.
- security.py: HTTP(S), credentials, hostname, DNS and public-IP checks.
- quality.py: normalizes best/audio/resolution choices and builds yt-dlp selectors.
- engine.py: wraps yt-dlp and finds/validates the final output.
- tasks.py: bounded work queue, state transitions, progress snapshots, cancellation and file retention.
- rate_limit.py: process-local sliding-window request limiter.
- errors.py: domain errors and user-safe error strings.

## Task lifecycle

~~~text
queued -> resolving -> downloading -> processing -> completed
   |          |             |              |
   +----------+-------------+--------------+--> failed
   +----------+-------------+--------------+--> cancelling -> cancelled
~~~

The worker reports actual yt-dlp progress events. The Android app polls GET /api/tasks/{id}, displays the state/bytes/speed/percent, can issue DELETE to cancel, and fetches /file only after completion.

## Client layers

- Kotlin: Compose UI and Android MediaStore integration; task state is fetched from API.
- C11: stable C ABI over libcurl, bounded JSON response, streaming transfer, progress callback and temp-file rename.
- C++: higher-level client which reuses the C SDK.
- Rust: platform-independent state/progress/quality/filename primitives.
- Java, Swift and C#: additional platform clients, with their current feature limits documented in clients/README.md.

## Process model (critical)

Task registry, queue capacity and rate limiting are in process memory. The Docker image therefore starts Gunicorn with one web worker and multiple request threads. The task executor runs downloads concurrently in its own bounded worker pool.

This is intentionally a single-instance MVP. A process restart discards task metadata; multiple web workers or replicas cannot share task state. Before scaling out, replace the in-memory task manager with a shared Redis-backed queue and persistent task records, and move output files to shared object storage.

## Security boundary

The server rejects non-HTTP(S), credential-bearing and initially private/local URLs. This first DNS check is not a complete SSRF sandbox because extractors may follow redirects or resolve later URLs independently. Production deployment must enforce outbound network restrictions at the container/host level. See SECURITY.md.
