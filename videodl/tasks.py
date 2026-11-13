from __future__ import annotations

import shutil
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from .engine import YtDlpEngine
from .errors import (
    DownloadCancelled,
    TaskNotFound,
    TaskNotReady,
    TaskQueueFull,
    public_error,
)
from .task_store import TaskStore


TERMINAL_STATES = {"completed", "failed", "cancelled"}
INTERRUPTED_TASK_ERROR = "服务进程已重启，原下载未能继续。请重新创建下载任务。"


@dataclass
class DownloadTask:
    id: str
    url: str
    quality: str
    state: str = "queued"
    downloaded_bytes: int = 0
    total_bytes: int | None = None
    speed_bytes_per_second: float | None = None
    eta_seconds: int | None = None
    filename: str | None = None
    size_bytes: int | None = None
    error: str | None = None
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)
    work_dir: Path | None = None
    file_path: Path | None = None
    cancel_event: threading.Event = field(default_factory=threading.Event)


class DownloadTaskManager:
    """Bounded in-process executor backed by durable SQLite task metadata.

    The queue and running workers are intentionally process-local. Task
    snapshots and completed artifact references survive a process restart.
    In-flight work is marked failed on recovery rather than falsely reported
    as running; automatic resume is a separate feature.
    """

    def __init__(
        self,
        engine: YtDlpEngine,
        download_dir: Path,
        max_workers: int = 2,
        max_queue: int = 8,
        retention_seconds: int = 3600,
        store_path: Path | None = None,
    ):
        self.engine = engine
        self.download_dir = Path(download_dir)
        self.download_dir.mkdir(parents=True, exist_ok=True)
        self.retention_seconds = retention_seconds
        self.store = TaskStore(
            store_path or (self.download_dir.parent / "tasks.sqlite3")
        )
        self._executor = ThreadPoolExecutor(
            max_workers=max_workers, thread_name_prefix="videodl-worker"
        )
        self._capacity = threading.BoundedSemaphore(max_workers + max_queue)
        self._tasks: dict[str, DownloadTask] = {}
        self._lock = threading.RLock()
        self._restore_tasks()

    def _record(self, task: DownloadTask) -> dict:
        return {
            "id": task.id,
            "url": task.url,
            "quality": task.quality,
            "state": task.state,
            "downloaded_bytes": task.downloaded_bytes,
            "total_bytes": task.total_bytes,
            "speed_bytes_per_second": task.speed_bytes_per_second,
            "eta_seconds": task.eta_seconds,
            "filename": task.filename,
            "size_bytes": task.size_bytes,
            "error": task.error,
            "created_at": task.created_at,
            "updated_at": task.updated_at,
            "work_dir": str(task.work_dir) if task.work_dir else None,
            "file_path": str(task.file_path) if task.file_path else None,
        }

    def _persist_locked(self, task: DownloadTask) -> None:
        self.store.save(self._record(task))

    def _restore_tasks(self) -> None:
        now = time.time()
        with self._lock:
            for row in self.store.load_all():
                task = DownloadTask(
                    id=row["id"],
                    url=row["url"],
                    quality=row["quality"],
                    state=row["state"],
                    downloaded_bytes=int(row["downloaded_bytes"] or 0),
                    total_bytes=row["total_bytes"],
                    speed_bytes_per_second=row["speed_bytes_per_second"],
                    eta_seconds=row["eta_seconds"],
                    filename=row["filename"],
                    size_bytes=row["size_bytes"],
                    error=row["error"],
                    created_at=float(row["created_at"]),
                    updated_at=float(row["updated_at"]),
                    work_dir=Path(row["work_dir"]) if row["work_dir"] else None,
                    file_path=Path(row["file_path"]) if row["file_path"] else None,
                )

                if task.state not in TERMINAL_STATES:
                    task.state = "failed"
                    task.error = INTERRUPTED_TASK_ERROR
                    task.updated_at = now
                    if task.work_dir:
                        shutil.rmtree(task.work_dir, ignore_errors=True)
                        task.work_dir = None
                    task.file_path = None
                    task.filename = None
                    task.size_bytes = None
                    self._persist_locked(task)
                elif task.state == "completed" and (
                    task.file_path is None or not task.file_path.is_file()
                ):
                    task.state = "failed"
                    task.error = "服务重启后未找到已完成的文件，请重新创建下载任务。"
                    task.updated_at = now
                    task.file_path = None
                    task.filename = None
                    task.size_bytes = None
                    self._persist_locked(task)

                self._tasks[task.id] = task

        self.prune_expired()

    def submit(self, url: str, quality: str) -> dict:
        self.prune_expired()
        if not self._capacity.acquire(blocking=False):
            raise TaskQueueFull("下载队列已满，请稍后再试。")

        task = DownloadTask(id=uuid.uuid4().hex, url=url, quality=quality)
        try:
            with self._lock:
                self._tasks[task.id] = task
                self._persist_locked(task)
            self._executor.submit(self._run_task, task.id)
        except Exception:
            with self._lock:
                self._tasks.pop(task.id, None)
                self.store.delete_many([task.id])
            self._capacity.release()
            raise
        return self.get(task.id)

    def _run_task(self, task_id: str) -> None:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                self._capacity.release()
                return
            if task.cancel_event.is_set():
                task.state = "cancelled"
                task.error = "下载已取消。"
                task.updated_at = time.time()
                self._persist_locked(task)
                self._capacity.release()
                return
            task.state = "resolving"
            task.updated_at = time.time()
            task.work_dir = self.download_dir / task.id
            self._persist_locked(task)

        try:
            task.work_dir.mkdir(parents=True, exist_ok=False)
            last_persisted = 0.0

            def on_progress(event: dict) -> None:
                nonlocal last_persisted
                if task.cancel_event.is_set():
                    raise DownloadCancelled("下载已取消。")

                status = event.get("status")
                now_monotonic = time.monotonic()
                with self._lock:
                    if task.cancel_event.is_set():
                        raise DownloadCancelled("下载已取消。")

                    previous_state = task.state
                    if status == "downloading":
                        task.state = "downloading"
                        task.downloaded_bytes = int(
                            event.get("downloaded_bytes")
                            or event.get("downloaded_bytes_estimate")
                            or 0
                        )
                        total = event.get("total_bytes") or event.get("total_bytes_estimate")
                        task.total_bytes = int(total) if total else None
                        speed = event.get("speed")
                        task.speed_bytes_per_second = float(speed) if speed else None
                        eta = event.get("eta")
                        task.eta_seconds = int(eta) if eta is not None else None
                    elif status == "finished":
                        task.state = "processing"
                    task.updated_at = time.time()

                    # Avoid one SQLite transaction for every tiny yt-dlp
                    # progress event while still saving state transitions and
                    # periodic progress snapshots.
                    should_persist = (
                        task.state != previous_state
                        or status == "finished"
                        or now_monotonic - last_persisted >= 1.0
                    )
                    if should_persist:
                        self._persist_locked(task)
                        last_persisted = now_monotonic

            output = self.engine.download_video(
                task.url, task.quality, task.work_dir, progress_hook=on_progress
            )
            with self._lock:
                if task.cancel_event.is_set():
                    raise DownloadCancelled("下载已取消。")
                task.file_path = output
                task.filename = output.name
                task.size_bytes = output.stat().st_size
                task.state = "completed"
                task.updated_at = time.time()
                self._persist_locked(task)
        except DownloadCancelled:
            with self._lock:
                task.state = "cancelled"
                task.error = "下载已取消。"
                task.updated_at = time.time()
                self._persist_locked(task)
            if task.work_dir:
                shutil.rmtree(task.work_dir, ignore_errors=True)
        except Exception as error:
            with self._lock:
                task.state = "failed"
                task.error = public_error(error)
                task.updated_at = time.time()
                self._persist_locked(task)
            if task.work_dir:
                shutil.rmtree(task.work_dir, ignore_errors=True)
        finally:
            self._capacity.release()

    def get(self, task_id: str) -> dict:
        self.prune_expired()
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                raise TaskNotFound("下载任务不存在或已过期。")
            return self._snapshot(task)

    def list_active(self) -> list[dict]:
        with self._lock:
            return [
                self._snapshot(task)
                for task in self._tasks.values()
                if task.state not in TERMINAL_STATES
            ]

    def cancel(self, task_id: str) -> dict:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                raise TaskNotFound("下载任务不存在或已过期。")
            if task.state in TERMINAL_STATES:
                return self._snapshot(task)
            task.cancel_event.set()
            task.state = "cancelling"
            task.updated_at = time.time()
            self._persist_locked(task)
            return self._snapshot(task)

    def get_file(self, task_id: str) -> tuple[Path, str]:
        self.prune_expired()
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                raise TaskNotFound("下载任务不存在或已过期。")
            if task.state != "completed" or task.file_path is None:
                raise TaskNotReady("任务尚未完成，暂时没有可下载文件。")
            if not task.file_path.is_file():
                raise TaskNotFound("下载文件已过期或被清理。")
            return task.file_path, task.filename or task.file_path.name

    def prune_expired(self) -> None:
        cutoff = time.time() - self.retention_seconds
        expired: list[DownloadTask] = []
        with self._lock:
            for task_id, task in list(self._tasks.items()):
                if task.state in TERMINAL_STATES and task.updated_at < cutoff:
                    expired.append(self._tasks.pop(task_id))
            if expired:
                self.store.delete_many(task.id for task in expired)

        for task in expired:
            if task.work_dir:
                shutil.rmtree(task.work_dir, ignore_errors=True)

    @staticmethod
    def _snapshot(task: DownloadTask) -> dict:
        total = task.total_bytes
        percent = None
        if total and total > 0:
            percent = min(100.0, task.downloaded_bytes * 100.0 / total)
        return {
            "id": task.id,
            "state": task.state,
            "progress": {
                "downloaded_bytes": task.downloaded_bytes,
                "total_bytes": total,
                "speed_bytes_per_second": task.speed_bytes_per_second,
                "eta_seconds": task.eta_seconds,
                "percent": percent,
            },
            "filename": task.filename,
            "size_bytes": task.size_bytes,
            "error": task.error,
            "created_at": task.created_at,
            "updated_at": task.updated_at,
        }

    def shutdown(self, wait: bool = True) -> None:
        with self._lock:
            for task in self._tasks.values():
                if task.state not in TERMINAL_STATES:
                    task.cancel_event.set()
                    task.state = "cancelling"
                    task.updated_at = time.time()
                    self._persist_locked(task)
        self._executor.shutdown(wait=wait, cancel_futures=False)
