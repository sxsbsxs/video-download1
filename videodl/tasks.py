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


TERMINAL_STATES = {"completed", "failed", "cancelled"}


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
    """Bounded, in-process task queue. It is not a distributed job broker."""

    def __init__(
        self,
        engine: YtDlpEngine,
        download_dir: Path,
        max_workers: int = 2,
        max_queue: int = 8,
        retention_seconds: int = 3600,
    ):
        self.engine = engine
        self.download_dir = Path(download_dir)
        self.download_dir.mkdir(parents=True, exist_ok=True)
        self.retention_seconds = retention_seconds
        self._executor = ThreadPoolExecutor(
            max_workers=max_workers, thread_name_prefix="videodl-worker"
        )
        self._capacity = threading.BoundedSemaphore(max_workers + max_queue)
        self._tasks: dict[str, DownloadTask] = {}
        self._lock = threading.RLock()

    def submit(self, url: str, quality: str) -> dict:
        self.prune_expired()
        if not self._capacity.acquire(blocking=False):
            raise TaskQueueFull("下载队列已满，请稍后再试。")

        task = DownloadTask(id=uuid.uuid4().hex, url=url, quality=quality)
        with self._lock:
            self._tasks[task.id] = task
        try:
            self._executor.submit(self._run_task, task.id)
        except Exception:
            with self._lock:
                self._tasks.pop(task.id, None)
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
                task.updated_at = time.time()
                self._capacity.release()
                return
            task.state = "resolving"
            task.updated_at = time.time()
            task.work_dir = self.download_dir / task.id

        try:
            task.work_dir.mkdir(parents=True, exist_ok=False)

            def on_progress(event: dict) -> None:
                if task.cancel_event.is_set():
                    raise DownloadCancelled("下载已取消。")
                status = event.get("status")
                with self._lock:
                    if task.cancel_event.is_set():
                        raise DownloadCancelled("下载已取消。")
                    if status == "downloading":
                        task.state = "downloading"
                        task.downloaded_bytes = int(
                            event.get("downloaded_bytes") or event.get("downloaded_bytes_estimate") or 0
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
        except DownloadCancelled:
            with self._lock:
                task.state = "cancelled"
                task.error = "下载已取消。"
                task.updated_at = time.time()
            if task.work_dir:
                shutil.rmtree(task.work_dir, ignore_errors=True)
        except Exception as error:
            with self._lock:
                task.state = "failed"
                task.error = public_error(error)
                task.updated_at = time.time()
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
            return self._snapshot(task)

    def get_file(self, task_id: str) -> tuple[Path, str]:
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
        self._executor.shutdown(wait=wait, cancel_futures=False)
