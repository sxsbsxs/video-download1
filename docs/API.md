# VideoDL Engine API

Base path: /api. All request and response bodies are JSON unless an endpoint returns a media file.

Remote API endpoints must use HTTPS. Requests currently accept publicly accessible media URLs only. The url field accepts a direct link or copied share text containing a link. The server extracts exactly one URL, then applies the usual public-IP and SSRF checks.

## Health

GET /api/health

Example response:

~~~json
{
  "status": "ok",
  "engine": "yt-dlp",
  "task_api": true
}
~~~

This is a process health response; it does not verify that every external media site is available.

## Resolve video information

POST /api/info

Request:

~~~json
{
  "url": "https://example.com/public-video"
}
~~~

**URL input accepts share text**, not only a bare URL. The server extracts one HTTP(S) URL from surrounding Chinese/English text, line breaks, Markdown links, angle-bracket wrappers and common trailing punctuation. It also accepts a domain pasted without a scheme and adds HTTPS. The extracted URL is then passed through the same public-IP/SSRF validation as before.

Example request:

~~~json
{
  "url": "朋友分享给你一个视频： https://example.com/watch?id=42 。复制打开看看"
}
~~~

If the input contains multiple different URLs, the API returns HTTP 400 and asks the caller to submit one link at a time. It does not silently pick one.

Response fields include id, title, thumbnail, duration, uploader, webpage_url, extractor, formats and source_url (the cleaned URL extracted from the submitted text). Each format exposes an id and label; resolution entries may also include width, height, fps and extension. The response includes best and audio choices.

## List recent tasks for one app installation

`GET /api/tasks?limit=20`

This endpoint requires an `X-VideoDL-Owner` header containing a random client-generated token between 32 and 256 characters. The server stores only its SHA-256 digest and returns tasks associated with that token. A missing token receives HTTP `401`; a malformed token receives HTTP `400`. The limit range is 1–100.

Example:

~~~bash
curl -H "X-VideoDL-Owner: YOUR_RANDOM_DEVICE_TOKEN" \
  "https://engine.example/api/tasks?limit=20"
~~~

Clients that omit the token can still create and use tasks by their unguessable task ID, but those tasks are not included in a device's history list.

## Create an asynchronous download task

POST /api/tasks

Request:

~~~json
{
  "url": "https://example.com/public-video",
  "quality": "1080"
}
~~~

When a client wants the task to appear in its private history list, it should also send the same `X-VideoDL-Owner` header used by the history endpoint. The Android client also sends this header when polling, cancelling, retrying and fetching the completed file; task ownership is checked on each of those routes.

Quality values:

- best: best available video/audio combination.
- audio: extract audio and convert to MP3.
- 144 through 4320: choose a video at or below the requested height when supported by the extractor.

Successful response: HTTP 202 Accepted.

~~~json
{
  "task": {
    "id": "opaque-task-id",
    "state": "queued",
    "progress": {
      "downloaded_bytes": 0,
      "total_bytes": null,
      "speed_bytes_per_second": null,
      "eta_seconds": null,
      "percent": null
    },
    "filename": null,
    "size_bytes": null,
    "error": null,
    "created_at": 0,
    "updated_at": 0
  },
  "status_url": "/api/tasks/opaque-task-id"
}
~~~

## List recent tasks

`GET /api/tasks?limit=20`

Returns recent tasks in reverse creation order. `limit` must be between 1 and 100. This endpoint is intended for the app's download-history view. Task metadata is read from SQLite, so records remain visible after a process restart, subject to the configured retention period.

## Query task state

GET /api/tasks/{task_id}

Returns HTTP 200 with the task object. Task states:

| State | Meaning |
|---|---|
| queued | Accepted and waiting for a worker |
| resolving | yt-dlp is resolving the media |
| downloading | Media bytes are being downloaded |
| processing | Download completed; FFmpeg or post-processing is running |
| cancelling | Cancellation was requested |
| completed | Output file is ready |
| failed | Task failed; error contains a safe user-facing message |
| cancelled | Task cancellation completed |

Progress values may be null until yt-dlp reports them. Percent is derived from actual transferred bytes and is capped at 100.

## Cancel a task

DELETE /api/tasks/{task_id}

Returns the updated task. A running task may temporarily remain in cancelling until yt-dlp reaches a cancellation checkpoint. A completed task is not retroactively cancelled.

## Download the completed file

GET /api/tasks/{task_id}/file

Returns the file as an attachment when the task is completed. Returns HTTP 409 if processing is not finished, and HTTP 404 if the task/file is missing or expired.

## Compatibility download endpoint

POST /api/download remains available for older clients. It holds the HTTP request open until the file is ready. New clients should use the asynchronous task routes so they can poll status and cancel.

## Error codes

| HTTP | Meaning |
|---|---|
| 400 | Invalid URL or quality |
| 404 | Task does not exist or has expired |
| 409 | Task file is not ready |
| 413 | Request body is too large |
| 422 | Extraction or download failed |
| 429 | Rate limit reached or bounded task queue is full |

## Runtime semantics

Task metadata is stored in SQLite at TASK_DB_PATH (default: /tmp/videodl/tasks.sqlite3). Completed tasks and their file references survive a process restart when the database and download volume are persistent. In-flight tasks are recovered as failed with an interruption message; they are not automatically resumed. The executor queue and rate limiter remain process-local, so deploy one web worker per instance. Task-state GET requests have a separate higher rate limit because clients poll frequently; creating tasks and extracting metadata remain under the regular per-IP limit.
