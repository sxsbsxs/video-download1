import unittest
from unittest.mock import patch

from app import app, validate_public_url


class UrlValidationTests(unittest.TestCase):
    def test_rejects_non_http_scheme(self):
        with self.assertRaises(ValueError):
            validate_public_url("file:///etc/passwd")

    def test_rejects_localhost(self):
        with self.assertRaises(ValueError):
            validate_public_url("http://localhost:8080/video")

    @patch("app.socket.getaddrinfo", return_value=[(2, 1, 6, "", ("127.0.0.1", 443))])
    def test_rejects_private_resolved_address(self, _mock_dns):
        with self.assertRaises(ValueError):
            validate_public_url("https://example.test/video")


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.client = app.test_client()

    def test_health(self):
        response = self.client.get("/api/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json["status"], "ok")

    def test_info_requires_valid_url(self):
        response = self.client.post("/api/info", json={"url": "file:///etc/passwd"})
        self.assertEqual(response.status_code, 400)

    def test_numeric_quality_passes_validation(self):
        from unittest.mock import patch
        with patch("app.validate_public_url", return_value="https://example.com/video"), \
             patch("app.download_slots.acquire", return_value=False):
            response = self.client.post(
                "/api/download",
                json={"url": "https://example.com/video", "quality": "1080"},
            )
        self.assertEqual(response.status_code, 429)


if __name__ == "__main__":
    unittest.main()
