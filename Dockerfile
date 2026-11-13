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

# Task state is process-local. Keep one web worker so task creation, polling
# and file retrieval share one in-memory registry. Downloads use a bounded
# background ThreadPoolExecutor and can still execute concurrently.
CMD ["sh", "-c", "gunicorn --bind 0.0.0.0:$PORT --workers 1 --threads 8 --timeout 900 app:app"]
