from __future__ import annotations

import hashlib
import shutil
import tempfile
import uuid
from pathlib import Path

from flask import jsonify, request, send_file
from werkzeug.utils import secure_filename

from .errors import (
    InvalidQuality,
    TaskNotFound,
    TaskNotReady,
    TaskQueueFull,
    public_error,
)
from .quality import normalize_quality
from .security import validate_public_url


def _payload() -> dict:
    data = request.get_json(silent=True)
    return data if isinstance(data, dict) else {}


def _owner_id() -> str | None:
    """Hash a client-held bearer token; never persist or log the raw token."""
    token = request.headers.get("X-VideoDL-Owner", "").strip()
    if not token:
        return ""
    if len(token) < 32 or len(token) > 256:
        return None
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _video_formats(info: dict) -> list[dict]:
    seen: set[int] = set()
    formats: list[dict] = []

    for item in info.get("formats") or []:
        height = item.get("height")
        if not item.get("url") or item.get("vcodec") == "none" or not height:
            continue
        try:
            height_value = int(height)
        except (TypeError, ValueError):
            continue
        if height_value in seen:
            continue
        seen.add(height_value)
        formats.append({
            "id": str(height_value),
            "label": f"{height_value}P",
            "width": item.get("width"),
            "height": height_value,
            "fps": item.get("fps"),
            "ext": item.get("ext"),
        })

    formats.sort(key=lambda item: item["height"], reverse=True)
    formats = formats[:12]
    formats.insert(0, {"id": "best", "label": "最佳画质"})
    formats.append({"id": "audio", "label": "仅音频"})
    return formats


def register_routes(app) -> None:
    @app.get("/api/health")
    def health():
        return jsonify(status="ok", engine="yt-dlp", task_api=True)

    @app.post("/api/info")
    def video_info():
        data = _payload()
        try:
            url = validate_public_url(data.get("url", ""))
            info = app.extensions["videodl.engine"].extract_info(url)
            formats = _video_formats(info)
            return jsonify({
                "id": info.get("id"),
                "title": info.get("title") or "未命名视频",
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration"),
                "uploader": info.get("uploader") or info.get("channel") or "",
                "webpage_url": info.get("webpage_url"),
                "extractor": info.get("extractor_key") or info.get("extractor"),
                "formats": formats,
            })
        except ValueError as error:
            return jsonify(error=str(error)), 400
        except Exception as error:
            app.logger.warning("Info extraction failed: %s", error)
            return jsonify(error=public_error(error)), 422

    @app.get("/api/tasks")
    def list_download_tasks():
        owner_id = _owner_id()
        if not owner_id:
            return jsonify(error="下载历史需要此设备的客户端令牌。"), 401
        if owner_id is None:
            return jsonify(error="客户端令牌格式无效。"), 400
        try:
            limit = int(request.args.get("limit", "20"))
        except (TypeError, ValueError):
            return jsonify(error="limit 必须是 1 到 100 之间的整数。"), 400
        if not 1 <= limit <= 100:
            return jsonify(error="limit 必须是 1 到 100 之间的整数。"), 400
        tasks = app.extensions["videodl.task_manager"].list_recent(limit, owner_id)
        return jsonify(tasks=tasks)

    @app.post("/api/tasks")
    def create_download_task():
        data = _payload()
        owner_id = _owner_id()
        if owner_id is None:
            return jsonify(error="客户端令牌格式无效。"), 400
        try:
            url = validate_public_url(data.get("url", ""))
            quality = normalize_quality(data.get("quality", "best"))
            task = app.extensions["videodl.task_manager"].submit(
                url, quality, owner_id=owner_id
            )
            return jsonify(task=task, status_url=f"/api/tasks/{task['id']}"), 202
        except ValueError as error:
            return jsonify(error=str(error)), 400
        except TaskQueueFull as error:
            return jsonify(error=str(error)), 429

    @app.get("/api/tasks/<task_id>")
    def get_download_task(task_id: str):
        try:
            task = app.extensions["videodl.task_manager"].get(task_id)
            return jsonify(task=task)
        except TaskNotFound as error:
            return jsonify(error=str(error)), 404

    @app.delete("/api/tasks/<task_id>")
    def cancel_download_task(task_id: str):
        try:
            task = app.extensions["videodl.task_manager"].cancel(task_id)
            status = 202 if task["state"] in {"cancelling", "cancelled"} else 200
            return jsonify(task=task), status
        except TaskNotFound as error:
            return jsonify(error=str(error)), 404

    @app.get("/api/tasks/<task_id>/file")
    def download_task_file(task_id: str):
        try:
            path, filename = app.extensions["videodl.task_manager"].get_file(task_id)
            safe_name = secure_filename(filename) or (
                f"videodl-{uuid.uuid4().hex[:8]}{Path(filename).suffix}"
            )
            return send_file(
                path,
                as_attachment=True,
                download_name=safe_name,
                conditional=True,
            )
        except TaskNotFound as error:
            return jsonify(error=str(error)), 404
        except TaskNotReady as error:
            return jsonify(error=str(error)), 409

    @app.post("/api/download")
    def download_video():
        """Compatibility endpoint for older synchronous clients."""
        data = _payload()
        try:
            url = validate_public_url(data.get("url", ""))
            quality = normalize_quality(data.get("quality", "best"))
        except ValueError as error:
            return jsonify(error=str(error)), 400

        sync_slots = app.extensions["videodl.sync_slots"]
        if not sync_slots.acquire(blocking=False):
            return jsonify(error="当前下载任务较多，请稍后再试。"), 429

        settings = app.config["VIDEODL_SETTINGS"]
        job_dir = Path(tempfile.mkdtemp(prefix="sync-", dir=settings.download_dir))
        try:
            output = app.extensions["videodl.engine"].download_video(
                url, quality, job_dir
            )
            safe_name = secure_filename(output.name) or (
                f"videodl-{uuid.uuid4().hex[:8]}{output.suffix}"
            )
            response = send_file(
                output,
                as_attachment=True,
                download_name=safe_name,
                conditional=True,
            )

            def cleanup() -> None:
                shutil.rmtree(job_dir, ignore_errors=True)
                sync_slots.release()

            response.call_on_close(cleanup)
            return response
        except Exception as error:
            shutil.rmtree(job_dir, ignore_errors=True)
            sync_slots.release()
            app.logger.warning("Synchronous download failed: %s", error)
            return jsonify(error=public_error(error)), 422
