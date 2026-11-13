import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from videodl.config import Settings


class SettingsTests(unittest.TestCase):
    def test_overrides_are_applied_and_paths_normalized(self):
        with tempfile.TemporaryDirectory() as temp:
            settings = Settings.from_env().with_overrides({
                "DOWNLOAD_DIR": temp,
                "MAX_CONCURRENT_DOWNLOADS": 3,
                "TASK_QUEUE_SIZE": 0,
            })
        self.assertIsInstance(settings.download_dir, Path)
        self.assertEqual(settings.task_db_path, Path(temp) / "tasks.sqlite3")
        self.assertEqual(settings.max_concurrent_downloads, 3)
        self.assertEqual(settings.task_queue_size, 0)

    def test_database_path_defaults_under_custom_download_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"DOWNLOAD_DIR": temp}, clear=False):
                os.environ.pop("TASK_DB_PATH", None)
                settings = Settings.from_env()
        self.assertEqual(settings.download_dir, Path(temp))
        self.assertEqual(settings.task_db_path, Path(temp) / "tasks.sqlite3")

    def test_explicit_database_path_takes_precedence(self):
        with tempfile.TemporaryDirectory() as temp:
            db_path = Path(temp) / "custom.sqlite3"
            with patch.dict(os.environ, {
                "DOWNLOAD_DIR": temp,
                "TASK_DB_PATH": str(db_path),
            }):
                settings = Settings.from_env()
        self.assertEqual(settings.task_db_path, db_path)

    def test_invalid_resource_limits_are_rejected(self):
        with self.assertRaises(ValueError):
            Settings.from_env().with_overrides({"MAX_FILE_MB": 0})
        with self.assertRaises(ValueError):
            Settings.from_env().with_overrides({"TASK_QUEUE_SIZE": -1})


if __name__ == "__main__":
    unittest.main()
