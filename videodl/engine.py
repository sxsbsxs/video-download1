from __future__ import annotations

from pathlib import Path
from typing import Callable

import yt_dlp
from werkzeug.utils import secure_filename

from .config import Settings
from .errors import DownloadCancelled
from .quality import selection_for


ProgressHook = Callable[[dict], None]


class YtDlpEngine:
    """Thin, testable adapter around yt-dlp and FFmpeg."""

    def __init__(self, settings: Settings):
        self.settings = settings

    def _base_options(self, **extra) -> dict:
        options = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "socket_timeout": 20,
            "retries": 2,
            "extractor_retries": 1,
            "cachedir": False,
        }
        options.update(extra)
        return options

    def extract_info(self, url: str) -> dict:
        with yt_dlp.YoutubeDL(self._base_options(skip_download=True)) as downloader:
            info = downloader.extract_info(url, download=False)
        if info.get("duration") and info["duration"] > self.settings.max_duration_seconds:
            raise ValueError(
                f"视频过长，当前上限为 {self.settings.max_duration_seconds // 60} 分钟。"
            )
        return info

    def download_video(
        self,
        url: str,
        quality: str,
        output_dir: Path,
        progress_hook: ProgressHook | None = None,
    ) -> Path:
        selection = selection_for(quality)
        output_dir.mkdir(parents=True, exist_ok=True)

        def duration_filter(info, *, incomplete=False):
            duration = info.get("duration")
            if duration and duration > self.settings.max_duration_seconds:
                return (
                    "视频超过时长限制 "
                    f"({self.settings.max_duration_seconds // 60} 分钟)"
                )
            return None

        hooks = []
        if progress_hook is not None:
            hooks.append(progress_hook)

        options = self._base_options(
            format=selection.format_selector,
            outtmpl=str(output_dir / "%(title).120B-%(id)s.%(ext)s"),
            merge_output_format=selection.extension,
            postprocessors=list(selection.postprocessors),
            max_filesize=self.settings.max_file_mb * 1024 * 1024,
            match_filter=duration_filter,
            restrictfilenames=True,
            progress_hooks=hooks,
        )

        with yt_dlp.YoutubeDL(options) as downloader:
            result = downloader.download([url])
        if result not in (None, 0):
            raise RuntimeError("yt-dlp 未能完成下载。")

        allowed = {".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".opus"}
        candidates = [
            path for path in output_dir.iterdir()
            if path.is_file() and path.suffix.lower() in allowed
            and not path.name.endswith(".part")
        ]
        if not candidates:
            raise RuntimeError("下载结束，但没有找到可用的输出文件。")

        output = max(candidates, key=lambda path: path.stat().st_size)
        if output.stat().st_size <= 0:
            raise RuntimeError("下载结果为空文件。")
        if output.stat().st_size > self.settings.max_file_mb * 1024 * 1024:
            raise RuntimeError("文件超过服务器允许的大小限制。")

        safe_name = secure_filename(output.name)
        if safe_name and safe_name != output.name:
            renamed = output.with_name(safe_name)
            output.rename(renamed)
            output = renamed
        return output

    @staticmethod
    def check_cancelled(cancel_event) -> None:
        if cancel_event.is_set():
            raise DownloadCancelled("下载已取消。")
