from __future__ import annotations

import threading
import time
from collections import defaultdict, deque


class RateLimiter:
    """Process-local sliding-window limiter with bounded client bookkeeping."""

    def __init__(self, limit: int, window_seconds: float = 60.0, max_clients: int = 10000):
        self.limit = limit
        self.window_seconds = window_seconds
        self.max_clients = max_clients
        self._events: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        cutoff = now - self.window_seconds
        with self._lock:
            events = self._events[key]
            while events and events[0] <= cutoff:
                events.popleft()

            if len(events) >= self.limit:
                return False

            events.append(now)
            if len(self._events) > self.max_clients:
                empty_keys = [client for client, values in self._events.items() if not values]
                for client in empty_keys:
                    self._events.pop(client, None)
                while len(self._events) > self.max_clients:
                    oldest = min(self._events, key=lambda client: self._events[client][0])
                    self._events.pop(oldest, None)
            return True
