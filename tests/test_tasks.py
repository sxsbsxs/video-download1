import tempfile
import threading
import time
import unittest
from pathlib import Path

from videodl.task_store import TaskStore
from videodl.tasks import DownloadTaskManager, INTERRUPTED_TASK_ERROR


class FakeEngine:
    def download_video(self, _url, _quality, output_dir, progress_hook=None):
        output_dir.mkdir(parents=True, exist_ok=True)
        if progress_hook:
            progress_hook({
                "status": "downloading",
                "downloaded_bytes": 50,
                "total_bytes": 100,
                "speed": 500,
                "eta": 1,
            })
            progress_hook({"status": "finished"})
        path = output_dir / "test-video.mp4"
        path.write_bytes(b"video-bytes")
        return path


class BlockingEngine:
    def __init__(self):
        self.started = threading.Event()
        self.resume = threading.Event()

    def download_video(self, _url, _quality, output_dir, progress_hook=None):
        output_dir.mkdir(parents=True, exist_ok=True)
        if progress_hook:
            progress_hook({
                "status": "downloading",
                "downloaded_bytes": 10,
                "total_bytes": 100,
            })
        self.started.set()
        self.resume.wait(timeout=3)
        if progress_hook:
            progress_hook({"status": "downloading", "downloaded_bytes": 20, "total_bytes": 100})
        path = output_dir / "cancelled.mp4"
        path.write_bytes(b"not-used")
        return path


class DownloadTaskManagerTests(unittest.TestCase):
    def wait_for_state(self, manager, task_id, expected):
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            status = manager.get(task_id)
            if status["state"] == expected:
                return status
            time.sleep(0.01)
        self.fail("task did not reach state: " + expected)

    def test_task_progress_completion_and_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manager = DownloadTaskManager(
                FakeEngine(), root / "tasks", max_workers=1, max_queue=1,
                store_path=root / "tasks.sqlite3",
            )
            try:
                task = manager.submit("https://example.com/video", "720")
                completed = self.wait_for_state(manager, task["id"], "completed")
                self.assertEqual(completed["filename"], "test-video.mp4")
                self.assertEqual(completed["size_bytes"], len(b"video-bytes"))
                file_path, filename = manager.get_file(task["id"])
                self.assertEqual(filename, "test-video.mp4")
                self.assertEqual(file_path.read_bytes(), b"video-bytes")
            finally:
                manager.shutdown()

    def test_completed_task_and_file_survive_manager_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            db_path = root / "tasks.sqlite3"
            download_root = root / "tasks"

            first = DownloadTaskManager(
                FakeEngine(), download_root, max_workers=1, max_queue=1,
                store_path=db_path,
            )
            task = first.submit("https://example.com/video", "720")
            self.wait_for_state(first, task["id"], "completed")
            first.shutdown()

            restored = DownloadTaskManager(
                FakeEngine(), download_root, max_workers=1, max_queue=1,
                store_path=db_path,
            )
            try:
                status = restored.get(task["id"])
                self.assertEqual(status["state"], "completed")
                path, name = restored.get_file(task["id"])
                self.assertEqual(name, "test-video.mp4")
                self.assertEqual(path.read_bytes(), b"video-bytes")
            finally:
                restored.shutdown()

    def test_inflight_task_is_recovered_as_failed_not_stuck_running(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            db_path = root / "tasks.sqlite3"
            work_dir = root / "tasks" / "interrupted-task"
            work_dir.mkdir(parents=True)
            (work_dir / "partial.mp4.part").write_bytes(b"partial")

            store = TaskStore(db_path)
            now = time.time()
            store.save({
                "id": "interrupted-task",
                "url": "https://example.com/video",
                "quality": "best",
                "state": "downloading",
                "downloaded_bytes": 1250,
                "total_bytes": 5000,
                "speed_bytes_per_second": 256.0,
                "eta_seconds": 15,
                "filename": None,
                "size_bytes": None,
                "error": None,
                "created_at": now - 20,
                "updated_at": now,
                "work_dir": str(work_dir),
                "file_path": None,
            })

            manager = DownloadTaskManager(
                FakeEngine(), root / "tasks", max_workers=1, max_queue=1,
                store_path=db_path,
            )
            try:
                recovered = manager.get("interrupted-task")
                self.assertEqual(recovered["state"], "failed")
                self.assertEqual(recovered["error"], INTERRUPTED_TASK_ERROR)
                self.assertFalse(work_dir.exists())
            finally:
                manager.shutdown()

    def test_cancelled_task_is_not_exposed_as_completed(self):
        with tempfile.TemporaryDirectory() as temp:
            engine = BlockingEngine()
            manager = DownloadTaskManager(
                engine, Path(temp) / "tasks", max_workers=1, max_queue=1,
                store_path=Path(temp) / "tasks.sqlite3",
            )
            try:
                task = manager.submit("https://example.com/video", "best")
                self.assertTrue(engine.started.wait(timeout=2))
                manager.cancel(task["id"])
                engine.resume.set()
                cancelled = self.wait_for_state(manager, task["id"], "cancelled")
                self.assertEqual(cancelled["state"], "cancelled")
                with self.assertRaises(Exception):
                    manager.get_file(task["id"])
            finally:
                engine.resume.set()
                manager.shutdown()


if __name__ == "__main__":
    unittest.main()
