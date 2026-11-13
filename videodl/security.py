from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlparse

from .errors import InvalidVideoURL


def validate_public_url(raw: str) -> str:
    """Validate a public HTTP(S) URL before passing it to yt-dlp.

    This rejects private and special-use IPs returned by the initial DNS lookup.
    It is defense-in-depth, not a complete sandbox against DNS rebinding or
    redirects performed later by extractors; public deployments need egress
    firewall rules that block private, loopback, link-local and metadata ranges.
    """
    if not isinstance(raw, str) or not raw.strip() or len(raw) > 2048:
        raise InvalidVideoURL("请输入有效的视频网址。")

    value = raw.strip()
    try:
        parsed = urlparse(value)
        if parsed.scheme.lower() not in ("http", "https") or not parsed.hostname:
            raise InvalidVideoURL("仅支持 http 或 https 视频链接。")
        if parsed.username or parsed.password:
            raise InvalidVideoURL("网址中不能包含账号或密码信息。")
        port = parsed.port
    except ValueError as error:
        raise InvalidVideoURL("视频网址格式无效。") from error

    host = parsed.hostname.rstrip(".").lower()
    if host in {"localhost", "localhost.localdomain"} or host.endswith(".local"):
        raise InvalidVideoURL("不允许访问本机或局域网地址。")

    try:
        literal_ip = ipaddress.ip_address(host)
        addresses = [literal_ip]
    except ValueError:
        try:
            port = port or (443 if parsed.scheme.lower() == "https" else 80)
            results = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
            addresses = [ipaddress.ip_address(item[4][0]) for item in results]
        except socket.gaierror as error:
            raise InvalidVideoURL("无法解析该网址的域名，请检查链接。") from error
        except ValueError as error:
            raise InvalidVideoURL("视频网址域名无效。") from error

    if not addresses or any(not address.is_global for address in addresses):
        raise InvalidVideoURL("不允许访问内网或特殊用途 IP 地址。")

    return value
