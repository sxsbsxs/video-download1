# Deployment

## Docker

Build and run the engine:

~~~bash
docker build -t videodl-engine .
docker run --rm \
  -p 8080:8080 \
  -e MAX_CONCURRENT_DOWNLOADS=2 \
  -e TASK_QUEUE_SIZE=8 \
  -e TASK_RETENTION_SECONDS=3600 \
  -v videodl-data:/tmp/videodl \
  videodl-engine
~~~

The container installs FFmpeg and runs as a non-root user. The data volume preserves generated files across container recreation where the platform supports persistent volumes.

## Process count

Do not increase Gunicorn web workers above one in the current version. The task registry and rate limiter are process-local. The Docker command intentionally uses one worker and several request threads. Background downloads remain concurrent through the bounded task executor.

Adding replicas or multiple workers before using a shared task store can produce task-not-found responses and status desynchronization.

## Configuration

| Variable | Default | Purpose |
|---|---:|---|
| PORT | 8080 | HTTP listener |
| DOWNLOAD_DIR | /tmp/videodl | Temporary output root |
| MAX_DURATION_SECONDS | 7200 | Maximum media duration |
| MAX_FILE_MB | 1024 | Maximum completed file size |
| MAX_CONCURRENT_DOWNLOADS | 2 | Background worker count and sync endpoint slots |
| TASK_QUEUE_SIZE | 8 | Additional queued tasks |
| TASK_RETENTION_SECONDS | 3600 | In-memory task and artifact retention window |
| RATE_LIMIT_PER_MINUTE | 10 | Per-IP limit for task creation, info and other API requests |
| TASK_STATUS_RATE_LIMIT_PER_MINUTE | 240 | Separate per-IP budget for task-state polling |
| MAX_REQUEST_BYTES | 16384 | Maximum JSON request size |

## Health check

Use GET /api/health. A successful response confirms that the web process is answering; it does not test yt-dlp against every supported site or verify free disk space.

## Before public release

The current service has no authentication or per-user task ownership. Add these before exposing a shared instance to arbitrary users. Restrict outbound network access to mitigate SSRF through extractor redirects and CDN URLs. Use a shared task queue/database and object storage before adding multiple instances.
