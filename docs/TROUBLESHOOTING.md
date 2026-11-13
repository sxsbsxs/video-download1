# Troubleshooting

## Task status remains queued

Check that the queue has not reached its configured limit and that the download worker is not blocked by another long-running task. The default worker count is two.

## The task ID exists during creation but GET returns 404

The current task registry is process-local. All task routes for one instance must reach the same process. The Docker image uses one Gunicorn worker by design. Do not add a second worker or a second replica until a shared task store is implemented.

A 404 can also mean the server restarted, the in-memory task expired, or the task ID was copied incorrectly.

## The task remains in cancelling

Cancellation is cooperative. yt-dlp checks the task cancellation flag at its progress callback checkpoints. During a long extractor/network call, the UI can temporarily show cancelling before the worker reports cancelled.

## Download fails during FFmpeg processing

Confirm FFmpeg is installed and executable:

~~~bash
ffmpeg -version
~~~

The Docker image installs FFmpeg. Local development machines must install it separately.

## A resolution does not appear

The engine reports resolutions exposed by the site's extractor. A requested resolution is an upper bound, not a guarantee that the media source provides that height. Some streams need merging; FFmpeg is required.

## HTTP 429

The regular per-IP rate limiter or bounded task queue rejected the request. Task-state polling uses a separate higher rate budget. Wait, reduce concurrent submissions, or review RATE_LIMIT_PER_MINUTE, TASK_STATUS_RATE_LIMIT_PER_MINUTE and queue capacity.

## HTTP 422

The extractor or media operation failed. Confirm the URL is public and valid, yt-dlp is current, and the platform has not changed its extraction behavior. The API deliberately returns a sanitized message rather than exposing internal exception details.
