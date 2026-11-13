"""On-device yt-dlp bridge for the VideoDL Android application.

All extraction and media downloads run on the Android device.
"""
from __future__ import annotations

import glob
import html
import json
import os
import re

import yt_dlp


_URL_PATTERN = re.compile(
    r"""(?ix)
    (?<![a-z0-9@.-])
    (?:
        (?:https?://|//|www\.)[^\s<>"'\x60]+
        |
        (?<![/:])
        (?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}
        (?::[0-9]{1,5})?
        (?:/[^\s<>"'\x60]*)?
    )
    """
)
_TRAILING = ".,!?;:，。！？；：、)]}）】》」』>\\\"'"
_LEADING = "<([{（【《「『\\\"'"


def extract_video_url(raw: str) -> str:
    if not isinstance(raw, str) or not raw.strip():
        raise ValueError("请粘贴视频链接，或包含视频链接的分享文案。")
    if len(raw) > 8192:
        raise ValueError("输入内容过长，请只粘贴一段分享文案。")
    text = html.unescape(raw).strip()
    candidates = []
    for match in _URL_PATTERN.finditer(text):
        value = match.group(0).strip().lstrip(_LEADING)
        for punctuation in ("，", "。", "！", "？", "；", "、"):
            index = value.find(punctuation)
            if index >= 0:
                value = value[:index]
                break
        value = value.rstrip(_TRAILING)
        for marker in ("复制打开", "复制链接", "点击打开", "点开看看", "打开看看", "打开抖音", "长按复制"):
            index = value.find(marker)
            if index >= 0:
                value = value[:index].rstrip(_TRAILING)
                break
        if not value:
            continue
        if value.startswith("//"):
            value = "https:" + value
        elif not value.lower().startswith(("http://", "https://")):
            value = "https://" + value
        if len(value) > 2048:
            raise ValueError("识别到的视频链接过长。")
        if value not in candidates:
            candidates.append(value)
    if not candidates:
        raise ValueError("没有识别到视频链接，请检查分享文案后重试。")
    if len(candidates) != 1:
        raise ValueError("检测到多个链接，请一次只粘贴一个视频链接。")
    return candidates[0]


def _format_dimensions(fmt):
    """Read dimensions from explicit fields, then source-supplied resolution labels."""
    width = int(fmt.get("width") or 0)
    height = int(fmt.get("height") or 0)

    for field in ("resolution", "format_note", "format_id"):
        value = fmt.get(field)
        if not isinstance(value, str):
            continue

        if width <= 0 or height <= 0:
            match = re.search(r"(?<!\d)(\d{3,5})\s*[x×]\s*(\d{3,5})(?!\d)", value, re.IGNORECASE)
            if match:
                width = width or int(match.group(1))
                height = height or int(match.group(2))

        if height <= 0:
            match = re.search(r"(?<!\d)(\d{3,4})p(?!\d)", value, re.IGNORECASE)
            if match:
                height = int(match.group(1))

    return width, height


def _size_info(fmt, duration=None):
    """Use only size fields yt-dlp received from the source. Never guess from bitrate."""
    exact = fmt.get("filesize")
    if exact:
        return int(exact), False

    approximate = fmt.get("filesize_approx")
    if approximate:
        return int(approximate), True

    return 0, False


def _combined_size(video_format, audio_format, duration):
    video_size, video_approx = _size_info(video_format, duration)
    if audio_format is None:
        return video_size, video_approx

    audio_size, audio_approx = _size_info(audio_format, duration)
    if video_size <= 0 or audio_size <= 0:
        return 0, False
    return video_size + audio_size, video_approx or audio_approx


def _best_audio(formats):
    choices = [
        item for item in formats
        if item.get("vcodec") == "none"
        and item.get("acodec") not in (None, "none")
    ]
    if not choices:
        return None
    return max(choices, key=lambda item: (
        float(item.get("abr") or item.get("tbr") or 0),
        int(item.get("filesize") or item.get("filesize_approx") or 0),
    ))


def _thumbnail_urls(info):
    candidates = []
    for item in info.get("thumbnails") or []:
        if not isinstance(item, dict):
            continue
        url = item.get("url")
        if not isinstance(url, str) or not url.startswith(("https://", "http://")):
            continue
        width = int(item.get("width") or 0)
        height = int(item.get("height") or 0)
        candidates.append((width * height, url))

    candidates.sort(key=lambda item: item[0], reverse=True)
    ordered = [url for _, url in candidates]
    direct = info.get("thumbnail")
    if isinstance(direct, str) and direct.startswith(("https://", "http://")):
        ordered.append(direct)

    result = []
    for url in ordered:
        if url not in result:
            result.append(url)
        if len(result) >= 8:
            break
    return result


def inspect(raw_text):
    url = extract_video_url(raw_text)
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "socket_timeout": 25,
        "retries": 2,
        "extractor_retries": 2,
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)

    if not isinstance(info, dict):
        raise ValueError("来源没有返回可识别的视频信息。")

    formats = info.get("formats") or []
    duration = float(info.get("duration") or 0)
    best_audio = _best_audio(formats)
    video_options = []
    audio_options = []

    for item in formats:
        format_id = str(item.get("format_id") or "")
        if not format_id:
            continue
        vcodec = str(item.get("vcodec") or "none")
        acodec = str(item.get("acodec") or "none")
        ext = str(item.get("ext") or "mp4")
        if vcodec != "none":
            width, height = _format_dimensions(item)
            if height <= 0:
                continue
            audio_format = None
            audio_id = ""
            selected_audio_codec = acodec
            if acodec == "none":
                if best_audio is None:
                    continue
                audio_format = best_audio
                audio_id = str(best_audio.get("format_id") or "")
                selected_audio_codec = str(best_audio.get("acodec") or "")
            total_size, size_is_approx = _combined_size(item, audio_format, duration)
            video_options.append({
                "key": "v:" + format_id,
                "format_id": format_id,
                "audio_format_id": audio_id,
                "width": width,
                "height": height,
                "fps": float(item.get("fps") or 0),
                "dynamic_range": str(item.get("dynamic_range") or ""),
                "ext": ext,
                "vcodec": vcodec,
                "acodec": selected_audio_codec,
                "filesize": total_size,
                "filesize_is_approx": size_is_approx,
                "bitrate": float(item.get("tbr") or 0),
            })
        elif acodec != "none":
            size, size_approx = _size_info(item, duration)
            audio_options.append({
                "key": "a:" + format_id,
                "format_id": format_id,
                "audio_format_id": "",
                "width": 0,
                "height": 0,
                "fps": 0,
                "dynamic_range": "",
                "ext": ext,
                "vcodec": "",
                "acodec": acodec,
                "filesize": size,
                "filesize_is_approx": size_approx,
                "bitrate": float(item.get("abr") or item.get("tbr") or 0),
            })

    video_options.sort(
        key=lambda item: (item["height"], item["fps"], item["bitrate"], item["filesize"]),
        reverse=True,
    )
    audio_options.sort(
        key=lambda item: (item["bitrate"], item["filesize"]),
        reverse=True,
    )

    thumbnails = _thumbnail_urls(info)
    return json.dumps({
        "source_url": url,
        "title": info.get("title") or "未命名视频",
        "uploader": info.get("uploader") or info.get("channel") or "",
        "thumbnail_urls": thumbnails,
        "duration": int(duration),
        "video_formats": video_options,
        "audio_formats": audio_options,
    }, ensure_ascii=False)


def _resolve_downloaded_file(ydl, info, directory, filename):
    """Resolve the final media path even when yt-dlp changes its extension."""
    candidates = []

    def add_candidate(value):
        if isinstance(value, str) and value:
            candidates.append(value)

    if isinstance(info, dict):
        for field in ("filepath", "_filename", "filename"):
            add_candidate(info.get(field))
        for item in info.get("requested_downloads") or []:
            if isinstance(item, dict):
                for field in ("filepath", "_filename", "filename"):
                    add_candidate(item.get(field))

    try:
        add_candidate(ydl.prepare_filename(info))
    except Exception:
        pass

    ignored_suffixes = (
        ".part", ".ytdl", ".temp", ".json", ".description",
        ".jpg", ".jpeg", ".png", ".webp", ".vtt", ".srt", ".ass",
    )
    for candidate in candidates:
        path = os.path.abspath(candidate)
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            return path

    # A postprocessor can replace the expected extension after the transfer.
    for path in sorted(
        glob.glob(os.path.join(directory, filename + ".*")),
        key=os.path.getmtime,
        reverse=True,
    ):
        if not os.path.isfile(path) or path.lower().endswith(ignored_suffixes):
            continue
        if os.path.getsize(path) > 0:
            return path

    raise RuntimeError("下载结束，但没有找到最终媒体文件（可能仍在封装或合并）。")


def _download_one(url, format_id, directory, filename, listener, stage, progress_start=0.0, progress_span=95.0):
    template = os.path.join(directory, filename + ".%(ext)s")

    def progress_hook(data):
        if listener is None:
            return
        status = data.get("status")
        if status == "downloading":
            raw = str(data.get("_percent_str") or "")
            raw = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", raw).replace("%", "").strip()
            match = re.search(r"\d+(?:\.\d+)?", raw)
            try:
                percent = float(match.group(0)) if match else 0.0
            except (TypeError, ValueError):
                percent = 0.0

            if percent <= 0:
                downloaded = float(data.get("downloaded_bytes") or 0)
                total = float(data.get("total_bytes") or data.get("total_bytes_estimate") or 0)
                if total > 0:
                    percent = downloaded * 100.0 / total
                else:
                    fragment_index = float(data.get("fragment_index") or 0)
                    fragment_count = float(data.get("fragment_count") or 0)
                    if fragment_count > 0:
                        percent = fragment_index * 100.0 / fragment_count

            percent = min(100.0, max(0.0, percent))
            overall = min(95.0, progress_start + progress_span * percent / 100.0)
            eta = int(data.get("eta") or 0)
            speed = data.get("_speed_str") or ""
            if percent >= 99.9:
                message = stage + " · 数据接收完成，正在封装文件…"
            else:
                message = stage + " · " + str(round(percent, 1)) + "% " + str(speed)
            listener.onProgressUpdate(overall, eta, message)
        elif status == "finished":
            finished_at = min(95.0, progress_start + progress_span)
            listener.onProgressUpdate(finished_at, 0, stage + "数据接收完成，正在校验文件…")

    def postprocessor_hook(data):
        if listener is None:
            return
        hook_status = str(data.get("status") or "")
        processor = str(data.get("postprocessor") or "").replace("PP", "")
        if hook_status == "started":
            listener.onProgressUpdate(
                min(97.0, progress_start + progress_span + 1.0),
                0,
                stage + " · 正在处理" + (processor or "媒体") + "…",
            )
        elif hook_status == "finished":
            listener.onProgressUpdate(
                min(97.0, progress_start + progress_span + 1.5),
                0,
                stage + " · 文件处理完成，正在校验…",
            )

    options = {
        "format": format_id,
        "outtmpl": template,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "overwrites": True,
        "continuedl": True,
        "retries": 3,
        "fragment_retries": 5,
        "concurrent_fragment_downloads": 4,
        "socket_timeout": 30,
        "progress_hooks": [progress_hook],
        "postprocessor_hooks": [postprocessor_hook],
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        path = _resolve_downloaded_file(ydl, info, directory, filename)
    return path


def download_plan(url, video_format_id, audio_format_id, output_dir, listener=None, audio_only=False):
    os.makedirs(output_dir, exist_ok=True)
    if audio_only:
        path = _download_one(
            url, video_format_id, output_dir, "audio", listener, "音频",
            progress_start=0.0, progress_span=95.0,
        )
        if listener is not None:
            listener.onProgressUpdate(97.0, 0, "音频下载完成，正在检查文件…")
        return json.dumps({"main_path": path, "audio_path": ""})

    video_span = 46.0 if audio_format_id else 95.0
    video_path = _download_one(
        url, video_format_id, output_dir, "video", listener, "视频",
        progress_start=0.0, progress_span=video_span,
    )
    audio_path = ""
    if audio_format_id:
        audio_path = _download_one(
            url, audio_format_id, output_dir, "audio", listener, "音频",
            progress_start=47.0, progress_span=46.0,
        )
        if listener is not None:
            listener.onProgressUpdate(94.0, 0, "视频和音频流已下载，正在准备合并…")
    elif listener is not None:
        listener.onProgressUpdate(97.0, 0, "视频下载完成，正在检查文件…")

    return json.dumps({
        "main_path": video_path,
        "audio_path": audio_path,
    })
