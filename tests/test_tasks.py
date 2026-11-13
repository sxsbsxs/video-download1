import tempfile
import threading
import time
import unittest
from pathlib import Path

from videodl.tasks import DownloadTaskManager


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
            manager = DownloadTaskManager(
                FakeEngine(), Path(temp), max_workers=1, max_queue=1
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

    def test_cancelled_task_is_not_exposed_as_completed(self):
        with tempfile.TemporaryDirectory() as temp:
            engine = BlockingEngine()
            manager = DownloadTaskManager(
                engine, Path(temp), max_workers=1, max_queue=1
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
