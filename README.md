# VideoDL — 多平台视频下载应用

VideoDL 是以 **Android 原生应用** 为主的多平台视频下载项目。它不是网站项目，不包含网页前端；应用使用 Python + yt-dlp 引擎解析公开可访问的媒体，下载任务由队列管理，客户端可以同步查看任务状态与进度。

> 仅下载你拥有版权、已获授权或平台明确允许下载的内容。请遵守目标网站服务条款与当地法律。本项目不提供 DRM、付费墙或访问控制绕过功能。

## 技术分工

| 技术 | 实际职责 |
|---|---|
| Kotlin + Jetpack Compose | Android 原生 UI、封面展示、任务状态轮询、进度显示、取消任务、保存到 Downloads |
| Python + yt-dlp | 站点提取、元数据与格式清单、画质规则、FFmpeg 后处理、任务执行 |
| C11 + libcurl | 可复用网络 SDK、响应边界限制、下载流式写盘、传输进度、取消、原子文件落盘 |
| C++17 | 更高层原生客户端，复用 C SDK，不再复制一套 HTTP 传输逻辑 |
| Rust | 跨平台任务状态机、进度计算、质量解析和安全文件名规则 |
| Java | JVM 命令行/自动化客户端 |
| SwiftUI | macOS 原生客户端 |
| C# + Avalonia | 桌面客户端 |

## 现在有的真实功能

- 公开媒体 URL 校验和基本 SSRF 防护。
- yt-dlp 元数据解析：标题、封面、作者、时长、可用分辨率。
- best、指定分辨率、audio 三类下载选择。
- 后台任务队列：任务 ID、排队、解析、下载、处理、完成、失败和取消状态。
- 任务状态查询接口，包含已下载字节、总字节、速度、ETA 和百分比。
- SQLite 保存任务元数据和完成文件索引；服务重启后可恢复历史状态，未完成任务会明确标记为中断。
- Android 客户端从服务端读取真实任务进度；支持取消任务，并保留任务 ID 以便恢复轮询。
- GET /api/tasks 支持读取最近任务列表，为 Android 下载历史页提供数据。
- 完成后通过独立任务文件接口获取文件，写入 Downloads/VideoDL。
- C11 SDK 支持流式写文件、进度回调、取消、TLS 校验、临时文件和成功后原子重命名。
- C++ 客户端复用 C11 SDK。
- Python、C、Rust 等模块有独立测试；GitHub Actions 构建 Android、C、C++、Java、Rust、Swift 和 C#。

**目前还没有完成：** 持久化执行队列和下载自动续跑、Redis/多实例队列、断点续传、离线内置 yt-dlp、Android WorkManager 常驻下载、登录态下载和 DRM 处理。不要把这些未完成项当作已经支持的功能。

## 目录结构

~~~text
.
├── app.py                         # WSGI 入口
├── videodl/
│   ├── app_factory.py              # Flask 应用工厂、依赖装配
│   ├── api.py                      # API 路由和响应格式
│   ├── config.py                   # 环境变量与运行配置
│   ├── security.py                 # URL 与 IP 安全校验
│   ├── quality.py                  # 画质解析和 yt-dlp 格式选择
│   ├── engine.py                   # yt-dlp/FFmpeg 引擎适配器
│   ├── tasks.py                    # 有界任务队列和任务状态
│   ├── task_store.py               # SQLite 任务元数据仓库
│   ├── rate_limit.py               # 进程内滑动窗口限流
│   └── errors.py                   # 业务错误与用户安全提示
├── clients/
│   ├── android/                    # Kotlin Android App
│   ├── apple/                      # SwiftUI macOS App
│   ├── desktop/                    # C# Avalonia App
│   ├── cpp/                        # C++17 客户端
│   ├── java/                       # Java 客户端
│   └── rust/                       # Rust 客户端
├── native/
│   ├── c-client/                   # C11 SDK、CLI、单元测试
│   └── rust-core/                  # Rust 任务核心
├── tests/                          # Python 单元和 API 测试
├── docs/
│   ├── API.md                      # API 请求、状态和错误定义
│   ├── ARCHITECTURE.md             # 模块边界和数据流
│   ├── ANDROID.md                  # Android 客户端说明
│   ├── DEPLOYMENT.md               # 单实例部署说明
│   ├── TROUBLESHOOTING.md          # 状态同步和故障排查
│   ├── SECURITY.md                 # 威胁模型和部署注意事项
│   └── ROADMAP.md                  # 未完成工作和优先级
├── .github/workflows/              # 自动测试和多语言构建
├── Makefile                        # 常用构建和测试入口
└── Dockerfile                      # Python 引擎容器
~~~

## 快速启动 Python 引擎

需要 Python 3.12+ 和 FFmpeg：

~~~bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
~~~

默认监听 http://127.0.0.1:8080。远程客户端必须配置 HTTPS 地址。

## 下载任务 API

推荐使用异步任务接口，而不是旧的同步下载接口：

~~~bash
# 1. 创建任务，返回 task.id
curl -X POST http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/public-video","quality":"1080"}'

# 2. 查看最近任务
curl 'http://127.0.0.1:8080/api/tasks?limit=20'

# 3. 轮询任务（替换任务 ID）
curl http://127.0.0.1:8080/api/tasks/TASK_ID

# 4. 取消任务
curl -X DELETE http://127.0.0.1:8080/api/tasks/TASK_ID

# 5. 任务完成后获取文件
curl -L http://127.0.0.1:8080/api/tasks/TASK_ID/file -o video.mp4
~~~

完整接口字段和状态定义见 [docs/API.md](docs/API.md)。

## 测试和构建

~~~bash
make test
make test-c
make build-cpp
~~~

也可以直接运行：

~~~bash
python -m unittest discover -s tests -v
cmake -S native/c-client -B build/c-client
cmake --build build/c-client --parallel
ctest --test-dir build/c-client --output-on-failure
~~~

Android Studio 打开 clients/android；其他客户端的运行方法见 [clients/README.md](clients/README.md)。

## 部署时非常重要

任务元数据和完成文件索引保存在 SQLite（默认 `/tmp/videodl/tasks.sqlite3`），但下载执行队列、工作线程和限流器仍属于**单个 Python 进程**。Docker/Gunicorn 因此固定为 **1 个 web worker + 8 个请求线程**；实际下载并发由独立的有界线程池控制。不要随手将 Gunicorn workers 改成 2 或更多，否则创建任务和查询任务可能落在不同进程，客户端就会遇到状态不同步或任务不存在。

服务重启后，SQLite 中的历史任务仍可查询；已完成且文件位于持久卷中的任务可以继续获取文件。重启时正在执行的任务会被标记为“服务重启导致中断”，不会假装仍在运行，也不会自动续传。多实例/多 worker 方案仍需要共享队列/任务协调和共享文件存储。详见 [docs/SECURITY.md](docs/SECURITY.md)。

## License

MIT
