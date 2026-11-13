from videodl.app_factory import create_app
from videodl.security import validate_public_url

# Backwards-compatible WSGI entry point: gunicorn app:app
app = create_app()

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=int(__import__("os").getenv("PORT", "8080")),
        debug=False,
    )
