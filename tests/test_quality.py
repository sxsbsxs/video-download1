import unittest

from videodl.errors import InvalidQuality
from videodl.quality import normalize_quality, selection_for


class QualityTests(unittest.TestCase):
    def test_supported_modes(self):
        self.assertEqual(normalize_quality(" BEST "), "best")
        self.assertEqual(normalize_quality("audio"), "audio")
        self.assertEqual(normalize_quality("1080"), "1080")

    def test_resolution_boundaries(self):
        self.assertEqual(normalize_quality("144"), "144")
        self.assertEqual(normalize_quality("4320"), "4320")
        for value in ("143", "4321", "1080p", "-720", "", "1e3"):
            with self.subTest(value=value):
                with self.assertRaises(InvalidQuality):
                    normalize_quality(value)

    def test_audio_selection(self):
        selection = selection_for("audio")
        self.assertEqual(selection.extension, "mp3")
        self.assertEqual(selection.format_selector, "bestaudio/best")
        self.assertEqual(selection.postprocessors[0]["key"], "FFmpegExtractAudio")

    def test_resolution_selection(self):
        selection = selection_for("1080")
        self.assertIn("height<=1080", selection.format_selector)
        self.assertEqual(selection.extension, "mp4")


if __name__ == "__main__":
    unittest.main()
