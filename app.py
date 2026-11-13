from __future__ import annotations

import ipaddress
import os
import re
import shutil
import socket
import tempfile
import threading
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse

import yt_dlp
from flask import Flask, jsonify, render_template, request, send_file
from werkzeug.utils import secure_filename

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024
DOWNLOAD_DIR = Path(os.getenv("DOWNLOAD_DIR", "/tmp/videodl"))
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

MAX_DURATION = int(os.getenv("MAX_DURATION_SECONDS", "7200"))
MAX_FILE_MB = int(os.getenv("MAX_FILE_MB", "1024"))
MAX_CONCURRENT = int(os.getenv("MAX_CONCURRENT_DOWNLOADS", "2"))
RATE_LIMIT = int(os.getenv("RATE_LIMIT_PER_MINUTE", "10"))
download_slots = threading.BoundedSemaphore(MAX_CONCURRENT)
rate_lock = threading.Lock()
requests_by_ip: dict[str, list[float]] = {}


def rate_limited() -> bool:
    now = time.time()
    ip = request.remote_addr or "unknown"
    with rate_lock:
        recent = [t for t in requests_by_ip.get(ip, []) if now - t < 60]
        if len(recent) >= RATE_LIMIT:
            requests_by_ip[ip] = recent
            return True
        recent.append(now)
        requests_by_ip[ip] = recent
    return False


@app.before_request
def apply_rate_limit():
    if request.path.startswith("/api/") and request.path != "/api/health":
        if rate_limited():
            return jsonify(error="请求过于频繁，请稍后再试。"), 429


def validate_public_url(raw: str) -> str:
    if not isinstance(raw, str) or len(raw) > 2048:
        raise ValueError("请输入有效的视频网址。")
    parsed = urlparse(raw.strip())
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise ValueError("仅支持 http 或 https 视频链接。")
    if parsed.username or parsed.password:
        raise ValueError("网址中不能包含账号或密码信息。")
    host = parsed.hostname.rstrip(".").lower()
    if host in {"localhost", "localhost.localdomain"} or host.endswith(".local"):
        raise ValueError("不允许访问本机或局域网地址。")
    try:
        addresses = socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80))
        for item in addresses:
            ip = ipaddress.ip_address(item[4][0])
            if not ip.is_global:
                raise ValueError("不允许访问内网或特殊用途 IP 地址。")
    except socket.gaierror:
        raise ValueError("无法解析该网址的域名，请检查链接。")
    except ValueError as exc:
        if "does not appear" in str(exc):
            raise ValueError("视频网址域名无效。")
        raise
    return raw.strip()


def duration_filter(info, *, incomplete=False):
    duration = info.get("duration")
    if duration and duration > MAX_DURATION:
        return f"视频超过时长限制（{MAX_DURATION // 60} 分钟）"
    return None


def ydl_options(**extra):
    base = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 2,
        "extractor_retries": 1,
        "cachedir": False,
    }
    base.update(extra)
    return base


def public_error(exc: Exception) -> str:
    message = str(exc)
    if "Unsupported URL" in message:
        return "暂不支持这个网站或链接格式。"
    if "Private video" in message or "login" in message.lower():
        return "该内容可能需要登录或没有公开访问权限；当前版本不处理登录内容。"
    if "HTTP Error 403" in message:
        return "目标网站拒绝了请求，请稍后重试或确认该视频可公开访问。"
    if "HTTP Error 404" in message:
        return "视频不存在或已被删除。"
    return "解析失败：请确认链接有效、内容可公开访问，并稍后重试。"


@app.get("/")
def index():
    return render_template("index.html")


@app.get("/api/health")
def health():
    return jsonify(status="ok", engine="yt-dlp")


@app.post("/api/info")
def video_info():
    data = request.get_json(silent=True) or {}
    try:
        url = validate_public_url(data.get("url", ""))
        with yt_dlp.YoutubeDL(ydl_options(skip_download=True)) as ydl:
            info = ydl.extract_info(url, download=False)
        if info.get("duration") and info["duration"] > MAX_DURATION:
            return jsonify(error=f"视频过长，当前上限为 {MAX_DURATION // 60} 分钟。"), 413

        formats = []
        seen = set()
        for fmt in info.get("formats", []):
            if not fmt.get("url") or fmt.get("vcodec") == "none":
                continue
            height = fmt.get("height")
            if not height:
                continue
            label = f'{height}P'
            if label not in seen:
                formats.append({"id": str(height), "label": label})
                seen.add(label)
        formats.sort(key=lambda x: int(x["id"]), reverse=True)
        formats = formats[:8]
        formats.insert(0, {"id": "best", "label": "最佳画质"})
        return jsonify({
            "title": info.get("title") or "未命名视频",
            "thumbnail": info.get("thumbnail"),
            "duration": info.get("duration"),
            "uploader": info.get("uploader") or info.get("channel") or "",
            "formats": formats,
        })
    except ValueError as exc:
        return jsonify(error=str(exc)), 400
    except Exception as exc:
        app.logger.warning("Info extraction failed: %s", exc)
        return jsonify(error=public_error(exc)), 422


@app.post("/api/download")
def download_video():
    data = request.get_json(silent=True) or {}
    try:
        url = validate_public_url(data.get("url", ""))
    except ValueError as exc:
        return jsonify(error=str(exc)), 400

    quality = str(data.get("quality", "best"))
    if quality not in {"best", "audio"} and not re.fullmatch(r"\d{2,4}", quality):
        return jsonify(error="画质选项无效。"), 400

    if not download_slots.acquire(blocking=False):
        return jsonify(error="当前下载任务较多，请稍后再试。"), 429

    job_dir = Path(tempfile.mkdtemp(prefix="job-", dir=DOWNLOAD_DIR))
    try:
        if quality == "audio":
            fmt = "bestaudio/best"
            postprocessors = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}]
            ext = "mp3"
        else:
            fmt = "bestvideo*+bestaudio/best" if quality == "best" else f"bestvideo[height<={quality}]+bestaudio/best[height<={quality}]/best"
            postprocessors = [{"key": "FFmpegVideoRemuxer", "preferedformat": "mp4"}]
            ext = "mp4"

        opts = ydl_options(
            format=fmt,
            outtmpl=str(job_dir / "%(title).120B-%(id)s.%(ext)s"),
            merge_output_format=ext,
            postprocessors=postprocessors,
            max_filesize=MAX_FILE_MB * 1024 * 1024,
            match_filter=duration_filter,
            restrictfilenames=True,
        )
        with yt_dlp.YoutubeDL(opts) as ydl:
            ydl.download([url])

        candidates = [p for p in job_dir.iterdir() if p.is_file() and p.suffix.lower() in {".mp4", ".mkv", ".webm", ".mp3", ".m4a"}]
        if not candidates:
            raise RuntimeError("下载完成但没有找到输出文件。")
        output = max(candidates, key=lambda p: p.stat().st_size)
        if output.stat().st_size > MAX_FILE_MB * 1024 * 1024:
            raise RuntimeError("文件超过服务器允许的大小限制。")

        filename = secure_filename(output.name) or f"videodl-{uuid.uuid4().hex[:8]}{output.suffix}"
        response = send_file(output, as_attachment=True, download_name=filename, conditional=True)
        response.call_on_close(lambda: (shutil.rmtree(job_dir, ignore_errors=True), download_slots.release()))
        return response
    except Exception as exc:
        shutil.rmtree(job_dir, ignore_errors=True)
        download_slots.release()
        app.logger.warning("Download failed: %s", exc)
        return jsonify(error=public_error(exc)), 422


@app.errorhandler(413)
def too_large(_error):
    return jsonify(error="请求内容过大。"), 413


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "8080")), debug=False)
