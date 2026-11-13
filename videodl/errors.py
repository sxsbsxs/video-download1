class VideoDLError(Exception):
    """Base class for expected VideoDL failures."""


class InvalidVideoURL(VideoDLError, ValueError):
    pass


class InvalidQuality(VideoDLError, ValueError):
    pass


class TaskQueueFull(VideoDLError):
    pass


class TaskNotFound(VideoDLError):
    pass


class TaskNotReady(VideoDLError):
    pass


class DownloadCancelled(VideoDLError):
    pass


def public_error(error: Exception) -> str:
    message = str(error)
    lowered = message.lower()

    if "unsupported url" in lowered:
        return "暂不支持这个网站或链接格式。"
    if "private video" in lowered or "login" in lowered or "sign in" in lowered:
        return "该内容可能需要登录或没有公开访问权限；当前版本不处理登录内容。"
    if "http error 403" in lowered or "403 forbidden" in lowered:
        return "目标网站拒绝了请求，请稍后重试或确认该视频可公开访问。"
    if "http error 404" in lowered or "404 not found" in lowered:
        return "视频不存在或已被删除。"
    if isinstance(error, (InvalidVideoURL, InvalidQuality)):
        return message
    return "解析或下载失败：请确认链接有效、内容可公开访问，并稍后重试。"
