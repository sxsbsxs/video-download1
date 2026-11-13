"""Regression tests for on-device thumbnail and format metadata."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "clients" / "android" / "app" / "src" / "main" / "python" / "videodl_mobile.py"
SPEC = importlib.util.spec_from_file_location("videodl_mobile", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
mobile = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mobile)


class FakeYoutubeDL:
    def __init__(self, options):
        self.options = options

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def extract_info(self, url, download=False):
        return {
            "title": "Sample video",
            "uploader": "Sample channel",
            "duration": 42,
            "thumbnail": "https://img.example.com/medium.jpg",
            "thumbnails": [
                {"url": "https://img.example.com/low.jpg", "width": 320, "height": 180},
                {"url": "https://img.example.com/high.jpg", "width": 1280, "height": 720},
            ],
            "formats": [
                {
                    "format_id": "299",
                    "width": 3840,
                    "height": 2160,
                    "fps": 60,
                    "dynamic_range": "HDR10",
                    "ext": "mp4",
                    "vcodec": "av01.0.08M.08",
                    "acodec": "none",
                    "filesize": 8_000_000,
                    "tbr": 5000,
                },
                {
                    "format_id": "137",
                    "width": 1920,
                    "height": 1080,
                    "fps": 30,
                    "dynamic_range": "SDR",
                    "ext": "mp4",
                    "vcodec": "avc1.640028",
                    "acodec": "none",
                    "filesize_approx": 3_000_000,
                    "tbr": 2500,
                },
                {
                    "format_id": "134",
                    "width": 1280,
                    "height": 720,
                    "fps": 30,
                    "ext": "mp4",
                    "vcodec": "avc1.4d401f",
                    "acodec": "none",
                    "tbr": 1200,
                },
                {
                    "format_id": "140",
                    "vcodec": "none",
                    "acodec": "mp4a.40.2",
                    "ext": "m4a",
                    "abr": 128,
                    "filesize": 800_000,
                },
            ],
        }


class MobileFormatMetadataTests(unittest.TestCase):
    def get_result(self):
        with patch.object(mobile.yt_dlp, "YoutubeDL", FakeYoutubeDL):
            return json.loads(mobile.inspect("https://example.com/video"))

    def test_only_source_formats_are_returned_with_actual_quality_fields(self):
        result = self.get_result()
        self.assertEqual([item["format_id"] for item in result["video_formats"]], ["299", "137", "134"])
        best = result["video_formats"][0]
        self.assertEqual(best["width"], 3840)
        self.assertEqual(best["height"], 2160)
        self.assertEqual(best["fps"], 60.0)
        self.assertEqual(best["dynamic_range"], "HDR10")
        self.assertEqual(best["audio_format_id"], "140")
        self.assertEqual(best["filesize"], 8_800_000)
        self.assertFalse(best["filesize_is_approx"])

    def test_approximate_file_sizes_are_marked(self):
        result = self.get_result()
        format_1080 = next(item for item in result["video_formats"] if item["format_id"] == "137")
        self.assertEqual(format_1080["filesize"], 3_800_000)
        self.assertTrue(format_1080["filesize_is_approx"])

    def test_missing_file_size_uses_bitrate_estimate_not_audio_size_only(self):
        result = self.get_result()
        format_720 = next(item for item in result["video_formats"] if item["format_id"] == "134")
        self.assertEqual(format_720["filesize"], 6_972_000)
        self.assertTrue(format_720["filesize_is_approx"])

    def test_thumbnail_urls_prefer_highest_resolution_and_limit_candidates(self):
        result = self.get_result()
        self.assertEqual(
            result["thumbnail_urls"][:2],
            ["https://img.example.com/high.jpg", "https://img.example.com/low.jpg"],
        )


if __name__ == "__main__":
    unittest.main()
