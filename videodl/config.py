from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    download_dir: Path
    task_db_path: Path
    max_duration_seconds: int = 7200
    max_file_mb: int = 1024
    max_concurrent_downloads: int = 2
    rate_limit_per_minute: int = 10
    task_status_rate_limit_per_minute: int = 240
    task_queue_size: int = 8
    task_retention_seconds: int = 3600
    max_request_bytes: int = 16 * 1024

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            download_dir=Path(os.getenv("DOWNLOAD_DIR", "/tmp/videodl")),
            task_db_path=Path(os.getenv("TASK_DB_PATH", "/tmp/videodl/tasks.sqlite3")),
            max_duration_seconds=_positive_int("MAX_DURATION_SECONDS", 7200),
            max_file_mb=_positive_int("MAX_FILE_MB", 1024),
            max_concurrent_downloads=_positive_int("MAX_CONCURRENT_DOWNLOADS", 2),
            rate_limit_per_minute=_positive_int("RATE_LIMIT_PER_MINUTE", 10),
            task_status_rate_limit_per_minute=_positive_int("TASK_STATUS_RATE_LIMIT_PER_MINUTE", 240),
            task_queue_size=_non_negative_int("TASK_QUEUE_SIZE", 8),
            task_retention_seconds=_positive_int("TASK_RETENTION_SECONDS", 3600),
            max_request_bytes=_positive_int("MAX_REQUEST_BYTES", 16 * 1024),
        )

    def with_overrides(self, values: dict) -> "Settings":
        mapping = {
            "DOWNLOAD_DIR": "download_dir",
            "TASK_DB_PATH": "task_db_path",
            "MAX_DURATION_SECONDS": "max_duration_seconds",
            "MAX_FILE_MB": "max_file_mb",
            "MAX_CONCURRENT_DOWNLOADS": "max_concurrent_downloads",
            "RATE_LIMIT_PER_MINUTE": "rate_limit_per_minute",
            "TASK_STATUS_RATE_LIMIT_PER_MINUTE": "task_status_rate_limit_per_minute",
            "TASK_QUEUE_SIZE": "task_queue_size",
            "TASK_RETENTION_SECONDS": "task_retention_seconds",
            "MAX_REQUEST_BYTES": "max_request_bytes",
        }
        updates = {}
        for external, internal in mapping.items():
            if external in values:
                updates[internal] = values[external]
        if "download_dir" in updates:
            updates["download_dir"] = Path(updates["download_dir"])
        if "task_db_path" in updates:
            updates["task_db_path"] = Path(updates["task_db_path"])
        elif "download_dir" in updates:
            updates["task_db_path"] = updates["download_dir"] / "tasks.sqlite3"
        updated = Settings(**{**self.__dict__, **updates})
        if updated.max_duration_seconds <= 0 or updated.max_file_mb <= 0:
            raise ValueError("视频时长和文件大小限制必须大于 0。")
        if (updated.max_concurrent_downloads <= 0 or updated.rate_limit_per_minute <= 0
                or updated.task_status_rate_limit_per_minute <= 0):
            raise ValueError("并发数和限流值必须大于 0。")
        if updated.task_queue_size < 0 or updated.task_retention_seconds <= 0:
            raise ValueError("队列长度不能小于 0，任务保留时间必须大于 0。")
        return updated


def _positive_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} 必须大于 0。")
    return value


def _non_negative_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value < 0:
        raise ValueError(f"{name} 不能小于 0。")
    return value
