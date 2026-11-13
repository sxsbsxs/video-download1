# VideoDL — 视频下载应用

VideoDL 是一个以 **Android 原生应用** 为主的多平台视频下载工具。项目不再开发或部署网站；用户通过应用界面使用解析、画质选择、下载和文件管理功能。

> 仅下载你拥有版权、已获授权或平台明确允许下载的内容。请遵守目标网站服务条款与当地法律。本项目不提供 DRM、付费墙或访问控制绕过功能。

## 应用定位

- **主应用：Android**，使用 Kotlin + Jetpack Compose。
- **下载引擎：Python**，使用 yt-dlp 解析公开内容，并调用 FFmpeg 处理音视频。
- **原生扩展：Rust / C++**，用于高性能任务核心和底层媒体能力。
- **扩展客户端：Java / Swift / C#**，分别服务 JVM 工具、Apple 原生应用和桌面应用。

仓库中的 Flask 仅作为应用调用的引擎 API，不提供 HTML 页面，不是网站产品。当前 Android 客户端通过配置的 API 地址连接引擎服务。

## 功能现状

| 能力 | 当前状态 |
|---|---|
| Android 原生界面 | 已有基础版本 |
| 公开视频信息解析 | API 已实现 |
| 封面、标题、上传者和画质列表 | API 与 Android 界面已接入 |
| 最佳画质、指定分辨率、音频下载 | API 已实现 |
| 保存到 Android Downloads/VideoDL | 已接入 |
| JVM Java 客户端 | 有基础命令行客户端 |
| C++ 客户端 | 有基础命令行客户端 |
| Rust 任务核心 | 正在加入跨平台核心基础模块 |
| Apple Swift 应用 | 规划为原生 Apple 客户端 |
| C# 桌面应用 | 规划为 Avalonia 桌面客户端 |
| 后台持续下载、真实进度、断点续传 | 尚未完成 |
| 本地离线 yt-dlp 引擎 | 尚未完成；目前 Android 依赖 API 服务 |

## 语言分工

| 语言 | 负责模块 | 为什么使用 |
|---|---|---|
| Kotlin | Android UI、协程、系统下载目录、通知与任务生命周期 | Android 主应用最合适 |
| Python | yt-dlp 提取器、FFmpeg 调度、解析 API | yt-dlp 原生生态 |
| Rust | 统一下载任务状态、进度数据模型、质量规则和安全文件名 | 内存安全、跨平台、适合核心逻辑 |
| C++ | 原生媒体处理/系统集成接口与工具 | 适合 NDK、FFmpeg 和底层库 |
| Java | JVM 自动化与兼容客户端 | 复用 JVM 生态；不是 Android 主 UI |
| Swift | Apple 平台原生应用 | 原生 Apple UI 和系统文件能力 |
| C# | Avalonia 桌面客户端 | Windows/Linux/macOS 桌面 UI |

不再保留只为增加语言统计而存在的薄封装。Go 和 TypeScript 示例客户端已移除；网站 UI 相关 HTML、CSS、JavaScript 也已移除。

## 目录

~~~text
.
├── app.py                         # Python 下载引擎 API（无网页路由）
├── requirements.txt
├── Dockerfile                     # 仅用于运行引擎 API
├── clients/
│   ├── android/                   # Kotlin + Jetpack Compose
│   ├── java/                      # Java JVM 客户端
│   ├── cpp/                       # C++ 客户端/原生集成入口
│   └── rust/                      # Rust 客户端
├── native/
│   └── rust-core/                 # 可复用的任务状态与质量规则核心
├── docs/
│   └── ARCHITECTURE.md
└── tests/
~~~

## Python 引擎 API

需要 Python 3.11+ 和 FFmpeg。

~~~bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
~~~

引擎默认监听 8080 端口。它只提供 API：

- GET /api/health：服务健康状态
- POST /api/info：解析标题、封面、时长、上传者和画质列表
- POST /api/download：生成下载文件流

请求示例：

~~~json
{"url":"https://example.com/public-video"}
~~~

下载请求还需要 quality，可选 best、audio 或数字分辨率，例如 1080。

## Android 应用

使用 Android Studio 打开 `clients/android`，运行 app 模块。应用中填写引擎 API 地址和公开视频链接，解析后选择画质并保存到系统 Downloads/VideoDL。

远程引擎必须使用 HTTPS。当前 Android 客户端是应用界面，yt-dlp 仍在 API 服务运行，并非完全离线版本。

## Java / C++ / Rust 工具

用法与依赖见 [clients/README.md](clients/README.md)。

## 引擎容器

~~~bash
docker build -t videodl-engine .
docker run --rm -p 8080:8080 videodl-engine
~~~

## 安全和部署边界

这是个人或小规模使用的起步项目，不是无限容量的公共下载服务。引擎目前对公开内容工作，不接受用户账号密码或 Cookie。免费主机可能休眠并限制 CPU、磁盘和带宽；高并发部署需要独立队列、共享限流存储和对象存储。

## License

MIT
