# VideoDL

一个基于 yt-dlp 的网页视频解析与下载工具。前端使用原生 HTML/CSS/JavaScript，后端使用 Python + Flask，支持 Docker / Render 部署。

> 仅下载你拥有版权、已获授权或平台明确允许下载的内容。请遵守目标网站的服务条款和当地法律。本项目不提供 DRM、付费墙或访问控制绕过功能。

## 功能

- 粘贴公开视频链接，获取标题、封面、时长和可用格式
- 选择格式后由服务器解析并生成下载
- 支持 yt-dlp 所支持的公开内容站点（具体取决于 yt-dlp 版本）
- 手机与桌面自适应界面
- Docker 一键部署，附 Render Blueprint
- 基础 URL 校验、请求频率限制、下载并发限制和文件大小/时长限制

## 本地运行

需要 Python 3.11+ 和 FFmpeg。

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
python app.py
```

打开 http://localhost:8080

## Docker

```bash
docker build -t videodl .
docker run --rm -p 8080:8080 videodl
```

## Render 部署

1. 将仓库连接到 Render。
2. 选择 **New + → Blueprint**，选择本仓库。
3. Render 会读取 `render.yaml` 创建 Web Service。
4. 免费实例可能休眠，首次访问需要等待唤醒；临时磁盘不保证持久保存。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `PORT` | `8080` | 服务端口 |
| `MAX_DURATION_SECONDS` | `7200` | 视频时长上限（秒） |
| `MAX_FILE_MB` | `1024` | 单文件大小上限（MB） |
| `MAX_CONCURRENT_DOWNLOADS` | `2` | 同时下载任务数 |
| `RATE_LIMIT_PER_MINUTE` | `10` | 每 IP 每分钟请求上限 |
| `DOWNLOAD_DIR` | `/tmp/videodl` | 临时下载目录 |

## 说明

这是一个面向个人/小规模使用的起步版本，不是无限容量的公共下载平台。Render 免费实例有休眠、资源、带宽和临时磁盘限制；高并发或大文件下载建议使用有明确额度的专用部署环境。不要在公开服务中加入未经授权的 Cookie 或账号凭据。

## License

MIT