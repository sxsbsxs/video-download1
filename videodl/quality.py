from __future__ import annotations

import re
from dataclasses import dataclass

from .errors import InvalidQuality


@dataclass(frozen=True)
class QualitySelection:
    value: str
    format_selector: str
    extension: str
    postprocessors: tuple[dict, ...]


def normalize_quality(value: object) -> str:
    quality = "best" if value is None else str(value).strip().lower()
    if quality in {"best", "audio"}:
        return quality
    if not re.fullmatch(r"\d{3,4}", quality):
        raise InvalidQuality("画质必须是 best、audio 或 144 到 4320 之间的分辨率。")
    height = int(quality)
    if not 144 <= height <= 4320:
        raise InvalidQuality("分辨率范围为 144P 至 4320P。")
    return str(height)


def selection_for(value: object) -> QualitySelection:
    quality = normalize_quality(value)
    if quality == "audio":
        return QualitySelection(
            value="audio",
            format_selector="bestaudio/best",
            extension="mp3",
            postprocessors=({
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            },),
        )

    if quality == "best":
        selector = "bestvideo*+bestaudio/best"
    else:
        selector = (
            f"bestvideo[height<={quality}]+bestaudio/"
            f"best[height<={quality}]/best"
        )

    return QualitySelection(
        value=quality,
        format_selector=selector,
        extension="mp4",
        postprocessors=({"key": "FFmpegVideoRemuxer", "preferedformat": "mp4"},),
    )
