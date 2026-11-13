# VideoDL

一个面向网页、Android 与命令行的多语言视频解析和下载项目。核心下载引擎使用 **Python + yt-dlp**；其他语言通过稳定的 HTTP API 协作。

> 仅下载你拥有版权、已获授权或平台明确允许下载的内容。请遵守目标网站服务条款与当地法律。本项目不提供 DRM、付费墙或访问控制绕过功能。

## 技术栈

| 语言 | 项目职责 |
|---|---|
| Python | 核心 Web API、yt-dlp 解析、下载与文件处理 |
| JavaScript | 当前网页交互界面 |
| TypeScript | 有类型定义的 Web / Node API SDK |
| Kotlin | Android 客户端（Jetpack Compose） |
| Java | JVM 命令行客户端 |
| C++ | 原生命令行客户端（libcurl + nlohmann/json） |
| Go | 轻量跨平台命令行客户端 |
| Rust | 类型安全的命令行客户端 |

各客户端统一调用 Python 服务端，不重复维护多份站点解析引擎。

## 核心功能

- 粘贴公开视频链接，获取标题、封面、时长和可用格式
- 选择最佳画质、指定分辨率或音频
- 服务器端使用 yt-dlp 解析并生成下载
- 手机与桌面自适应网页界面
- Docker 部署与 Render Blueprint
- 基础 URL 校验、请求频率限制、并发限制、时长和文件大小限制

## 本地运行服务端

需要 Python 3.11+ 和 FFmpeg。

~~~bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
~~~

打开 http://localhost:8080

## API

- GET /api/health：服务状态
- POST /api/info：解析视频信息，请求体包含 url
- POST /api/download：下载文件，请求体包含 url 和 quality

画质可选 best、audio 或数字分辨率（例如 1080）。

## 多语言客户端

详见 clients/README.md 和 docs/ARCHITECTURE.md。

## Docker

~~~bash
docker build -t videodl .
docker run --rm -p 8080:8080 videodl
~~~

## Render 部署

1. 将仓库连接到 Render。
2. 选择 New + → Blueprint，选择本仓库。
3. Render 会读取 render.yaml 创建 Web Service。
4. 免费实例可能休眠；首次访问可能需要等待唤醒，临时磁盘也不保证持久保存。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| PORT | 8080 | 服务端口 |
| MAX_DURATION_SECONDS | 7200 | 视频时长上限（秒） |
| MAX_FILE_MB | 1024 | 单文件大小上限（MB） |
| MAX_CONCURRENT_DOWNLOADS | 2 | 同时下载任务数 |
| RATE_LIMIT_PER_MINUTE | 10 | 每 IP 每分钟请求上限 |
| DOWNLOAD_DIR | /tmp/videodl | 临时下载目录 |

## 部署边界

这是个人/小规模使用的起步版本，不是无限容量的公共下载平台。免费托管服务可能有休眠、资源、带宽和临时磁盘限制；高并发或大文件下载应部署在有明确额度的环境中。当前服务端只处理可公开访问的内容，不接受用户账号密码或登录 Cookie。

## License

MIT
