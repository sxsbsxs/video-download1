FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN mkdir -p /tmp/videodl \
    && useradd --create-home --uid 10001 appuser \
    && chown -R appuser:appuser /app /tmp/videodl
USER appuser
EXPOSE 8080

# SQLite persists task metadata and completed artifact references. Keep one
# web worker because live executor state, cancellation events and rate limits
# are process-local. Downloads still run concurrently in a bounded executor.
CMD ["sh", "-c", "gunicorn --bind 0.0.0.0:$PORT --workers 1 --threads 8 --timeout 900 app:app"]
