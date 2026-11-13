import tempfile
import threading
import time
import unittest
from pathlib import Path

from videodl.task_store import TaskStore


class TaskStoreTests(unittest.TestCase):
    def make_record(self, task_id="task-1"):
        now = time.time()
        return {
            "id": task_id,
            "url": "https://example.com/public-video",
            "quality": "1080",
            "state": "queued",
            "downloaded_bytes": 0,
            "total_bytes": None,
            "speed_bytes_per_second": None,
            "eta_seconds": None,
            "filename": None,
            "size_bytes": None,
            "error": None,
            "created_at": now,
            "updated_at": now,
            "work_dir": None,
            "file_path": None,
        }

    def test_existing_database_gets_owner_column_during_migration(self):
        import sqlite3

        with tempfile.TemporaryDirectory() as temp:
            database = Path(temp) / "legacy.sqlite3"
            connection = sqlite3.connect(database)
            connection.execute(
                """
                CREATE TABLE download_tasks (
                    id TEXT PRIMARY KEY, url TEXT NOT NULL, quality TEXT NOT NULL,
                    state TEXT NOT NULL, downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                    total_bytes INTEGER, speed_bytes_per_second REAL, eta_seconds INTEGER,
                    filename TEXT, size_bytes INTEGER, error TEXT,
                    created_at REAL NOT NULL, updated_at REAL NOT NULL,
                    work_dir TEXT, file_path TEXT
                )
                """
            )
            connection.commit()
            connection.close()

            store = TaskStore(database)
            with store._connection() as migrated_connection:
                columns = {
                    row["name"]
                    for row in migrated_connection.execute(
                        "PRAGMA table_info(download_tasks)"
                    )
                }
            self.assertIn("owner_id", columns)

    def test_upsert_and_read_back_task(self):
        with tempfile.TemporaryDirectory() as temp:
            store = TaskStore(Path(temp) / "tasks.sqlite3")
            record = self.make_record()
            store.save(record)
            record["state"] = "downloading"
            record["downloaded_bytes"] = 512
            record["total_bytes"] = 2048
            record["updated_at"] = time.time()
            store.save(record)

            loaded = store.load_all()
            self.assertEqual(len(loaded), 1)
            self.assertEqual(loaded[0]["state"], "downloading")
            self.assertEqual(loaded[0]["downloaded_bytes"], 512)
            self.assertEqual(loaded[0]["total_bytes"], 2048)
            self.assertEqual(store.count(), 1)

    def test_delete_many_removes_only_requested_records(self):
        with tempfile.TemporaryDirectory() as temp:
            store = TaskStore(Path(temp) / "tasks.sqlite3")
            store.save(self.make_record("task-a"))
            store.save(self.make_record("task-b"))
            store.delete_many(["task-a"])
            self.assertEqual([row["id"] for row in store.load_all()], ["task-b"])

    def test_concurrent_writes_use_independent_connections(self):
        with tempfile.TemporaryDirectory() as temp:
            store = TaskStore(Path(temp) / "tasks.sqlite3")
            errors = []

            def writer(index):
                try:
                    store.save(self.make_record(f"task-{index}"))
                except Exception as error:
                    errors.append(error)

            threads = [
                threading.Thread(target=writer, args=(index,))
                for index in range(12)
            ]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join(timeout=5)

            self.assertFalse(errors, errors)
            self.assertEqual(store.count(), 12)


if __name__ == "__main__":
    unittest.main()
