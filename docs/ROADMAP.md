# Development roadmap

This roadmap describes planned work, not already shipped functionality.

## Priority 1 — reliability

- Add a shared Redis-backed queue/worker coordinator so multiple workers can coordinate live tasks; SQLite task metadata persistence is already implemented for a single instance.
- Add safe automatic retry/resume for interrupted tasks after the engine can verify partial-file and extractor support.
- Add authenticated task ownership, API tokens and per-user quotas before public multi-user hosting.
- Add structured logs, request IDs, task timing and disk-usage monitoring.
- Add cleanup worker for expired output artifacts and abandoned temporary folders.

## Priority 2 — downloader experience

- Android WorkManager integration for app-background downloads and reconnectable notifications.
- Download history backed by local Room database.
- Add automatic retry policy for transient network errors; manual retry from failed/cancelled task history is implemented.
- Real pause/resume and HTTP range support where the source and engine support it.
- User-selectable output container and audio format.

## Priority 3 — native integration

- Bind Rust core into Kotlin with JNI only after the state model is stable.
- Expose C SDK through Android NDK as an optional transport adapter.
- Expand C++ media adapters around FFmpeg only when a concrete performance need exists.
- Align metadata and download error models across Kotlin, Python, C and Rust.

## Priority 4 — release engineering

- Signed Android release APK and reproducible release workflow.
- Platform packaging for macOS and desktop clients.
- Dependency update automation, SBOM and release notes.

Avoid adding languages or modules just to inflate the file count. Each new file should own one tested responsibility.
