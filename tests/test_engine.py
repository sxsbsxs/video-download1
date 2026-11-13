import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from videodl.config import Settings
from videodl.engine import YtDlpEngine


class FakeYoutubeDL:
    def __init__(self, options):
        self.options = options
        self.downloaded_urls = []

    def __enter__(self):
        return self

    def __exit__(self, _type, _value, _traceback):
        return False

    def extract_info(self, _url, download=False):
        assert download is False
        return {"id": "abc123", "title": "Example", "duration": 20}

    def download(self, urls):
        self.downloaded_urls.extend(urls)
        hook = self.options["progress_hooks"][0]
        hook({
            "status": "downloading",
            "downloaded_bytes": 50,
            "total_bytes": 100,
            "speed": 200,
            "eta": 1,
        })
        output_template = Path(self.options["outtmpl"])
        (output_template.parent / "Example-abc123.mp4").write_bytes(b"video")
        return 0


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.settings = Settings.from_env().with_overrides({
            "MAX_DURATION_SECONDS": 600,
            "MAX_FILE_MB": 10,
        })
        self.engine = YtDlpEngine(self.settings)

    @patch("videodl.engine.yt_dlp.YoutubeDL")
    def test_extract_info_uses_safe_metadata_options(self, youtube_dl):
        fake = FakeYoutubeDL({})
        youtube_dl.return_value = fake

        info = self.engine.extract_info("https://example.com/video")

        self.assertEqual(info["id"], "abc123")
        options = youtube_dl.call_args.args[0]
        self.assertTrue(options["skip_download"])
        self.assertTrue(options["noplaylist"])
        self.assertFalse(options["cachedir"])

    @patch("videodl.engine.yt_dlp.YoutubeDL")
    def test_download_reports_progress_and_returns_real_output(self, youtube_dl):
        fake_instances = []

        def factory(options):
            fake = FakeYoutubeDL(options)
            fake_instances.append(fake)
            return fake

        youtube_dl.side_effect = factory
        events = []

        with tempfile.TemporaryDirectory() as temp:
            output = self.engine.download_video(
                "https://example.com/video",
                "1080",
                Path(temp),
                progress_hook=events.append,
            )
            self.assertTrue(output.is_file())
            self.assertEqual(output.read_bytes(), b"video")

        options = fake_instances[0].options
        self.assertIn("height<=1080", options["format"])
        self.assertEqual(options["max_filesize"], 10 * 1024 * 1024)
        self.assertEqual(events[0]["downloaded_bytes"], 50)


if __name__ == "__main__":
    unittest.main()
