# Android client

## Current behavior

The Android application is a Kotlin + Jetpack Compose client. It:

1. Connects to the configured HTTPS VideoDL Engine API.
2. Requests metadata from POST /api/info.
3. Displays title, uploader and thumbnail.
4. Creates a server task with POST /api/tasks.
5. Polls GET /api/tasks/{id} and renders the server-reported state and transfer progress.
6. Sends DELETE /api/tasks/{id} when the user requests cancellation.
7. Fetches the completed artifact and writes it to Downloads/VideoDL through MediaStore.
8. Reads GET /api/tasks?limit=20 to display recent downloads, reconnects to unfinished tasks, and can save a completed server-side task again.

The task identifier and main form values use Compose saveable state. The client also stores a random owner token in private app preferences; this token is sent only for task creation and history-list requests. When the activity is recreated and a task ID remains, the user can choose 恢复同步下载任务 to reconnect to the server-side task.

## Build

Open clients/android in Android Studio, or run:

~~~bash
gradle -p clients/android assembleDebug
~~~

CI uploads a debug APK artifact from the application build workflow.

## Known limitations

- Task metadata is stored in SQLite. In-flight tasks are marked failed after a server restart rather than automatically resumed.
- Background work is not yet managed by Android WorkManager. The UI coroutine may stop when the app process is killed.
- Task polling is periodic HTTP polling, not a WebSocket stream. The task-history view shows server-side tasks and does not yet provide local Room history.
- Output naming currently uses a generated VideoDL filename rather than the source's original title.
