import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app import app, validate_public_url
from videodl.errors import InvalidVideoURL


class UrlValidationTests(unittest.TestCase):
    def test_rejects_non_http_scheme(self):
        with self.assertRaises(InvalidVideoURL):
            validate_public_url("file:///etc/passwd")

    def test_rejects_localhost(self):
        with self.assertRaises(InvalidVideoURL):
            validate_public_url("http://localhost:8080/video")

    def test_rejects_credentials_in_url(self):
        with self.assertRaises(InvalidVideoURL):
            validate_public_url("https://user:secret@example.com/video")

    @patch("videodl.security.socket.getaddrinfo",
           return_value=[(2, 1, 6, "", ("127.0.0.1", 443))])
    def test_rejects_private_resolved_address(self, _mock_dns):
        with self.assertRaises(InvalidVideoURL):
            validate_public_url("https://example.test/video")


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_root_does_not_serve_a_website(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 404)

    def test_health(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["status"], "ok")
        self.assertTrue(response.json["task_api"])

    def test_info_requires_valid_url(self):
        response = self.client.post(
            "/api/info", json={"url": "file:///etc/passwd"}
        )
        self.assertEqual(response.status_code, 400)

    def test_task_history_rejects_invalid_limit(self):
        headers = {"X-VideoDL-Owner": self.OWNER_TOKEN}
        self.assertEqual(self.client.get("/api/tasks?limit=0", headers=headers).status_code, 400)
        self.assertEqual(self.client.get("/api/tasks?limit=101", headers=headers).status_code, 400)
        self.assertEqual(self.client.get("/api/tasks?limit=nope", headers=headers).status_code, 400)
        self.assertEqual(self.client.get("/api/tasks?limit=20").status_code, 401)

    def test_invalid_quality_rejected_before_queue(self):
        with patch("videodl.api.validate_public_url",
                   return_value="https://example.com/video"):
            response = self.client.post(
                "/api/tasks",
                json={"url": "https://example.com/video", "quality": "9999"},
            )
        self.assertEqual(response.status_code, 400)


class FakeEngine:
    def __init__(self):
        self.info = {
            "id": "video-1",
            "title": "Sample Video",
            "thumbnail": "https://example.com/thumb.jpg",
            "duration": 12,
            "uploader": "VideoDL Test",
            "formats": [
                {"url": "https://cdn.example/video.mp4", "height": 720,
                 "width": 1280, "vcodec": "h264", "ext": "mp4"},
                {"url": "https://cdn.example/video2.mp4", "height": 720,
                 "width": 1280, "vcodec": "h264", "ext": "mp4"},
                {"url": "https://cdn.example/video3.mp4", "height": 1080,
                 "width": 1920, "vcodec": "h264", "ext": "mp4"},
            ],
        }

    def extract_info(self, _url):
        return self.info

    def download_video(self, _url, _quality, output_dir, progress_hook=None):
        output_dir.mkdir(parents=True, exist_ok=True)
        if progress_hook:
            progress_hook({
                "status": "downloading",
                "downloaded_bytes": 5,
                "total_bytes": 10,
                "speed": 100,
                "eta": 1,
            })
            progress_hook({"status": "finished"})
        output = output_dir / "sample.mp4"
        output.write_bytes(b"sample-video")
        return output


class AsyncTaskApiTests(unittest.TestCase):
    OWNER_TOKEN = "test-device-owner-token-0123456789"

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        from videodl.app_factory import create_app

        self.app = create_app(
            {
                "TESTING": True,
                "DOWNLOAD_DIR": Path(self.temp_dir.name),
                "RATE_LIMIT_PER_MINUTE": 2,
                "TASK_STATUS_RATE_LIMIT_PER_MINUTE": 100,
                "MAX_CONCURRENT_DOWNLOADS": 1,
                "TASK_QUEUE_SIZE": 2,
            },
            engine=FakeEngine(),
        )
        self.client = self.app.test_client()

    def tearDown(self):
        self.app.extensions["videodl.task_manager"].shutdown()
        self.temp_dir.cleanup()

    def test_info_returns_unique_resolutions(self):
        with patch("videodl.api.validate_public_url",
                   return_value="https://example.com/video"):
            response = self.client.post(
                "/api/info", json={"url": "https://example.com/video"}
            )
        self.assertEqual(response.status_code, 200)
        ids = [item["id"] for item in response.json["formats"]]
        self.assertEqual(ids.count("720"), 1)
        self.assertIn("1080", ids)
        self.assertEqual(ids[0], "best")
        self.assertEqual(ids[-1], "audio")

    def test_task_completes_and_file_can_be_downloaded(self):
        with patch("videodl.api.validate_public_url",
                   return_value="https://example.com/video"):
            created = self.client.post(
                "/api/tasks",
                json={"url": "https://example.com/video", "quality": "720"},
                headers={"X-VideoDL-Owner": self.OWNER_TOKEN},
            )
        self.assertEqual(created.status_code, 202)
        task_id = created.json["task"]["id"]

        import time
        deadline = time.monotonic() + 5
        task = None
        while time.monotonic() < deadline:
            response = self.client.get(f"/api/tasks/{task_id}")
            task = response.json["task"]
            if task["state"] in {"completed", "failed", "cancelled"}:
                break
            time.sleep(0.02)

        self.assertEqual(task["state"], "completed")
        # Polling must use its own high-frequency budget, not the stricter
        # task-creation/info request limit.
        for _ in range(20):
            poll = self.client.get(f"/api/tasks/{task_id}")
            self.assertEqual(poll.status_code, 200)
        file_response = self.client.get(f"/api/tasks/{task_id}/file")
        self.assertEqual(file_response.status_code, 200)
        self.assertEqual(file_response.data, b"sample-video")
        file_response.close()

        history = self.client.get(
            "/api/tasks?limit=10",
            headers={"X-VideoDL-Owner": self.OWNER_TOKEN},
        )
        self.assertEqual(history.status_code, 200)
        self.assertEqual(
            self.client.get(
                "/api/tasks?limit=10",
                headers={"X-VideoDL-Owner": self.OWNER_TOKEN},
            ).status_code,
            200,
        )
        history_ids = [item["id"] for item in history.json["tasks"]]
        self.assertIn(task_id, history_ids)

        other_owner_history = self.client.get(
            "/api/tasks?limit=10",
            headers={"X-VideoDL-Owner": "another-device-owner-token-0123456789"},
        )
        self.assertEqual(other_owner_history.status_code, 200)
        self.assertNotIn(
            task_id,
            [item["id"] for item in other_owner_history.json["tasks"]],
        )


if __name__ == "__main__":
    unittest.main()
