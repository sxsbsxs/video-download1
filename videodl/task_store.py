from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any, Iterable


class TaskStore:
    """Small SQLite repository for task metadata and completion records.

    Each operation owns its SQLite connection. This keeps the store safe to use
    from Flask request threads and the bounded download worker threads without
    sharing sqlite connection objects across threads.
    """

    def __init__(self, database_path: Path):
        self.database_path = Path(database_path)
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(
            self.database_path,
            timeout=10.0,
            isolation_level="IMMEDIATE",
        )
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout = 10000")
        return connection

    def _initialize(self) -> None:
        with self._connect() as connection:
            connection.execute("PRAGMA journal_mode = WAL")
            connection.execute("PRAGMA synchronous = NORMAL")
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS download_tasks (
                    id TEXT PRIMARY KEY,
                    url TEXT NOT NULL,
                    quality TEXT NOT NULL,
                    state TEXT NOT NULL,
                    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                    total_bytes INTEGER,
                    speed_bytes_per_second REAL,
                    eta_seconds INTEGER,
                    filename TEXT,
                    size_bytes INTEGER,
                    error TEXT,
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL,
                    work_dir TEXT,
                    file_path TEXT
                )
                """
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_download_tasks_updated
                ON download_tasks(updated_at)
                """
            )

    def save(self, task: dict[str, Any]) -> None:
        """Insert or update one task snapshot in a single transaction."""
        values = (
            task["id"],
            task["url"],
            task["quality"],
            task["state"],
            task["downloaded_bytes"],
            task["total_bytes"],
            task["speed_bytes_per_second"],
            task["eta_seconds"],
            task["filename"],
            task["size_bytes"],
            task["error"],
            task["created_at"],
            task["updated_at"],
            task["work_dir"],
            task["file_path"],
        )
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO download_tasks (
                    id, url, quality, state, downloaded_bytes, total_bytes,
                    speed_bytes_per_second, eta_seconds, filename, size_bytes,
                    error, created_at, updated_at, work_dir, file_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    url=excluded.url,
                    quality=excluded.quality,
                    state=excluded.state,
                    downloaded_bytes=excluded.downloaded_bytes,
                    total_bytes=excluded.total_bytes,
                    speed_bytes_per_second=excluded.speed_bytes_per_second,
                    eta_seconds=excluded.eta_seconds,
                    filename=excluded.filename,
                    size_bytes=excluded.size_bytes,
                    error=excluded.error,
                    updated_at=excluded.updated_at,
                    work_dir=excluded.work_dir,
                    file_path=excluded.file_path
                """,
                values,
            )

    def load_all(self) -> list[dict[str, Any]]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM download_tasks ORDER BY created_at"
            ).fetchall()
        return [dict(row) for row in rows]

    def delete_many(self, task_ids: Iterable[str]) -> None:
        ids = list(task_ids)
        if not ids:
            return
        with self._connect() as connection:
            connection.executemany(
                "DELETE FROM download_tasks WHERE id = ?",
                ((task_id,) for task_id in ids),
            )

    def count(self) -> int:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT COUNT(*) AS total FROM download_tasks"
            ).fetchone()
        return int(row["total"])
