# Troubleshooting

## Task status remains queued

Check that the queue has not reached its configured limit and that the download worker is not blocked by another long-running task. The default worker count is two.

## The task ID exists during creation but GET returns 404

Task metadata is loaded from SQLite, but the live executor and task updates are process-local. All task routes for one instance must reach the same process. The Docker image uses one Gunicorn worker by design. Do not add a second worker or a second replica until a shared queue/worker coordinator is implemented.

A 404 can mean the task expired, the SQLite database/volume was replaced, or the task ID was copied incorrectly. After a server restart, an interrupted task should appear as `failed` with a restart message rather than disappear.

## A task is marked failed after a service restart

The task record survived in SQLite, but the current worker queue cannot resume a yt-dlp process that was terminated with the service. Submit a new task. Completed files can still be downloaded if the database and download directory were kept on persistent storage.

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
