from __future__ import annotations

import threading

from flask import Flask, jsonify, request

from .api import register_routes
from .config import Settings
from .engine import YtDlpEngine
from .rate_limit import RateLimiter
from .tasks import DownloadTaskManager


def create_app(config: dict | None = None, engine=None, task_manager=None) -> Flask:
    overrides = config or {}
    settings = Settings.from_env().with_overrides(overrides)

    app = Flask(__name__)
    app.config.update(overrides)
    app.config["MAX_CONTENT_LENGTH"] = settings.max_request_bytes
    app.config["VIDEODL_SETTINGS"] = settings

    settings.download_dir.mkdir(parents=True, exist_ok=True)
    engine = engine or YtDlpEngine(settings)
    task_manager = task_manager or DownloadTaskManager(
        engine=engine,
        download_dir=settings.download_dir / "tasks",
        max_workers=settings.max_concurrent_downloads,
        max_queue=settings.task_queue_size,
        retention_seconds=settings.task_retention_seconds,
        store_path=settings.task_db_path,
    )

    app.extensions["videodl.engine"] = engine
    app.extensions["videodl.task_manager"] = task_manager
    app.extensions["videodl.sync_slots"] = threading.BoundedSemaphore(
        settings.max_concurrent_downloads
    )
    app.extensions["videodl.rate_limiter"] = RateLimiter(
        limit=settings.rate_limit_per_minute
    )
    app.extensions["videodl.status_rate_limiter"] = RateLimiter(
        limit=settings.task_status_rate_limit_per_minute
    )

    @app.before_request
    def enforce_api_limits():
        if request.path.startswith("/api/") and request.path != "/api/health":
            client_key = request.remote_addr or "unknown"
            is_task_poll = (
                request.method == "GET"
                and (
                    request.path == "/api/tasks"
                    or (
                        request.path.startswith("/api/tasks/")
                    )
                )
            )
            limiter_key = (
                "videodl.status_rate_limiter"
                if is_task_poll else "videodl.rate_limiter"
            )
            if not app.extensions[limiter_key].allow(client_key):
                return jsonify(error="请求过于频繁，请稍后再试。"), 429
        return None

    register_routes(app)

    @app.errorhandler(413)
    def request_too_large(_error):
        return jsonify(error="请求内容过大。"), 413

    return app
