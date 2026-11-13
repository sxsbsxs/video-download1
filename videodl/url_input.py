from __future__ import annotations

import html
import re

from .errors import InvalidVideoURL


MAX_PASTED_TEXT_LENGTH = 8192
MAX_EXTRACTED_URL_LENGTH = 2048

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

_TRAILING_PUNCTUATION = ".,!?;:，。！？；：、)]}）】》」』>\"'"
_LEADING_WRAPPERS = "<([{（【《「『\"'"


def _clean_candidate(candidate: str) -> str:
    value = candidate.strip()
    value = value.lstrip(_LEADING_WRAPPERS)

    # Strip Chinese sentence punctuation even when the share app placed it
    # immediately after the URL without a separating space.
    for separator in ("，", "。", "！", "？", "；", "、"):
        position = value.find(separator)
        if position >= 0:
            value = value[:position]
            break

    value = value.rstrip(_TRAILING_PUNCTUATION)

    for suffix in (
        "复制打开",
        "复制链接",
        "点击打开",
        "点开看看",
        "打开看看",
    ):
        if value.endswith(suffix):
            value = value[: -len(suffix)].rstrip(_TRAILING_PUNCTUATION)
    return value


def extract_video_url(raw: str) -> str:
    """Extract one HTTP(S) link from share text and validate input shape."""
    if not isinstance(raw, str) or not raw.strip():
        raise InvalidVideoURL("请粘贴视频链接，或包含视频链接的分享文案。")
    if len(raw) > MAX_PASTED_TEXT_LENGTH:
        raise InvalidVideoURL("输入内容过长，请只粘贴一段视频分享文案。")

    text = html.unescape(raw).strip()
    candidates: list[str] = []
    for match in _URL_PATTERN.finditer(text):
        candidate = _clean_candidate(match.group(0))
        if not candidate:
            continue
        if candidate.startswith("//"):
            candidate = "https:" + candidate
        elif not candidate.lower().startswith(("http://", "https://")):
            candidate = "https://" + candidate
        if len(candidate) > MAX_EXTRACTED_URL_LENGTH:
            raise InvalidVideoURL("识别到的视频链接过长。")
        if candidate not in candidates:
            candidates.append(candidate)

    if not candidates:
        raise InvalidVideoURL("没有识别到视频链接，请检查分享文案后重试。")
    if len(candidates) > 1:
        raise InvalidVideoURL("检测到多个链接，请一次只粘贴一个视频链接。")
    return candidates[0]
