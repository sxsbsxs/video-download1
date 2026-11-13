# VideoDL — 多平台视频下载应用

VideoDL 以 Android 原生应用为主。Android App 内置 Python + yt-dlp 引擎，直接在手机上解析并下载媒体，不需要填写服务器地址、不需要部署 VideoDL API，也不需要租用服务器。仓库同时保留可选 Python 服务端和桌面/命令行客户端，均不是 Android App 的运行前提。

> 仅下载你拥有版权、已获授权或平台明确允许下载的内容。请遵守目标网站服务条款与当地法律。本项目不提供 DRM、付费墙或访问控制绕过功能。

## Android 版本

- Kotlin + Jetpack Compose 原生界面，采用清爽的 Google Material 风格。
- Chaquopy 内嵌 Python 3.13 和 yt-dlp，视频解析及下载在手机本机进行。
- 支持粘贴 URL、分享文案提取唯一链接、Android 系统分享和 HTTP(S) 链接打开。
- 画质选项由 yt-dlp 返回的源站格式列表生成，不写死 720p、1080p 或 4K。
- 展示来源实际提供的分辨率、帧率、动态范围/HDR（来源有提供时）、编码、封装格式、格式 ID 和文件大小。
- 同一分辨率有多个实际编码或容器版本时，可展开选择。
- 估算大小明确标注“约”；只知道分离流其中一段的大小时显示“大小未知”，不伪造完整文件体积。
- 视频、音频分开选择；分离流在设备本地通过 FFmpeg 做无损流复制封装。
- 下载完成后保存到系统 Downloads/VideoDL，并保留最近下载记录。

## APK 体积

Android 手机分发包仅构建 arm64-v8a，不再同时打包 x86_64 的 Python 与 native 库；FFmpeg 由 full 改为 minimal 变体，因为当前合并流程仅执行 stream copy，不需要可选音视频编码器。移除了 Material Icons Extended 图标全集和 debug UI tooling 依赖。不会为了缩小安装包而删除画质或降低下载分辨率。

注意：ARM64-only APK 适用于绝大多数现代 Android 手机，不适用于 x86_64 Android 模拟器。实际 APK 字节数由 GitHub Actions 构建日志报告。

## 技术分工

| 技术 | 实际职责 |
|---|---|
| Kotlin + Jetpack Compose | Android 原生 UI、格式选择、下载进度、保存到系统 Downloads |
| Chaquopy + Python + yt-dlp | Android 内置媒体站点解析和本机下载引擎 |
| FFmpegKit minimal | Android 本地 MP4/MKV 音视频流复制封装 |
| Flask + Python + yt-dlp | 可选服务端，供桌面或自动化客户端使用 |
| C11 + libcurl | 可复用流式网络 SDK、进度、取消、TLS 与原子文件落盘 |
| C++17 | 复用 C11 网络 SDK 的客户端 |
| Rust | 跨平台任务状态机、进度和质量核心 |
| Java | JVM 命令行客户端 |
| SwiftUI | macOS 客户端 |
| C# + Avalonia | 桌面客户端 |

## 目录结构

~~~text
.
├── app.py
├── videodl/                       # 可选 Python 服务端 API
├── clients/
│   ├── android/                   # Android 原生应用
│   ├── apple/                     # SwiftUI macOS 客户端
│   ├── desktop/                   # C# Avalonia 客户端
│   ├── cpp/                       # C++17 客户端
│   ├── java/                      # Java CLI
│   └── rust/                      # Rust CLI
├── native/
│   ├── c-client/                  # C11 SDK、CLI 与测试
│   └── rust-core/                 # Rust 任务核心
├── tests/
└── .github/workflows/             # 自动测试和多语言构建
~~~

## Android 构建

需要 Python 3.13、JDK 17、Android SDK 和 Gradle 8.9。可在 Android Studio 打开 clients/android，或在项目根目录运行：

~~~bash
gradle -p clients/android assembleDebug
~~~

APK 输出位置：

~~~text
clients/android/app/build/outputs/apk/debug/app-debug.apk
~~~

完整构建说明见 docs/ANDROID.md；第三方组件许可证见 clients/android/THIRD_PARTY_NOTICES.md。

## 可选 Python API

Android App 不需要此 API。需要给其他客户端使用时，可安装 Python 3.12+ 和 FFmpeg：

~~~bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
~~~

默认监听 http://127.0.0.1:8080。远程客户端必须配置 HTTPS 地址。

## 测试

~~~bash
python -m unittest discover -s tests -v
make test-c
make build-cpp
~~~

## License

MIT
