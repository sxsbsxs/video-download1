"""On-device yt-dlp bridge for the VideoDL Android application.

This module runs inside the app through Chaquopy. It does not contact a
VideoDL server: all extraction and media downloads run on the Android device.
"""
from __future__ import annotations

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


def _size_info(fmt):
    exact = fmt.get("filesize")
    if exact:
        return int(exact), False
    approximate = fmt.get("filesize_approx")
    if approximate:
        return int(approximate), True
    return 0, False


def _file_size(fmt):
    return _size_info(fmt)[0]


def _combined_size(video_format, audio_format):
    video_size, video_approx = _size_info(video_format)
    if audio_format is None:
        return video_size, video_approx

    audio_size, audio_approx = _size_info(audio_format)
    # Never report a partial stream size as if it were the merged file size.
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
        _file_size(item),
    ))


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
            height = int(item.get("height") or 0)
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
            total_size, size_is_approx = _combined_size(item, audio_format)
            video_options.append({
                "key": "v:" + format_id,
                "format_id": format_id,
                "audio_format_id": audio_id,
                "width": int(item.get("width") or 0),
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
            size, size_approx = _size_info(item)
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

    return json.dumps({
        "source_url": url,
        "title": info.get("title") or "未命名视频",
        "uploader": info.get("uploader") or info.get("channel") or "",
        "thumbnail": info.get("thumbnail") or "",
        "duration": int(info.get("duration") or 0),
        "video_formats": video_options,
        "audio_formats": audio_options,
    }, ensure_ascii=False)


def _download_one(url, format_id, directory, filename, listener, stage):
    template = os.path.join(directory, filename + ".%(ext)s")

    def progress_hook(data):
        if listener is None:
            return
        if data.get("status") == "downloading":
            raw = str(data.get("_percent_str") or "0").replace("%", "").strip()
            try:
                percent = float(re.sub(r"[^0-9.]", "", raw) or 0)
            except ValueError:
                percent = 0.0
            eta = int(data.get("eta") or 0)
            speed = data.get("_speed_str") or ""
            listener.onProgressUpdate(
                percent,
                eta,
                stage + " · " + str(round(percent, 1)) + "% " + str(speed),
            )
        elif data.get("status") == "finished":
            listener.onProgressUpdate(100.0, 0, stage + "完成，正在整理文件…")

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
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        path = ydl.prepare_filename(info)
    if not os.path.isfile(path) or os.path.getsize(path) <= 0:
        raise RuntimeError(stage + "下载结束，但没有找到输出文件。")
    return path


def download_plan(url, video_format_id, audio_format_id, output_dir, listener=None, audio_only=False):
    os.makedirs(output_dir, exist_ok=True)
    if audio_only:
        path = _download_one(url, video_format_id, output_dir, "audio", listener, "音频")
        return json.dumps({"main_path": path, "audio_path": ""})

    video_path = _download_one(url, video_format_id, output_dir, "video", listener, "视频")
    audio_path = ""
    if audio_format_id:
        audio_path = _download_one(url, audio_format_id, output_dir, "audio", listener, "音频")

    return json.dumps({
        "main_path": video_path,
        "audio_path": audio_path,
    })
