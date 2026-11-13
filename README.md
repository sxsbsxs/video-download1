# VideoDL

VideoDL 是以 Android 原生应用为主的视频下载工具。Android App 内置 Python + yt-dlp 引擎，在手机上完成视频解析、真实格式读取和下载，不需要用户配置服务器或 API。

## Android 功能

- Google Material 3 风格 UI，底部独立「首页 / 下载 / 设置」页面。
- 粘贴 URL 或整段分享文案，也支持 Android 系统分享。
- 显示 yt-dlp 从来源实际读取的分辨率、FPS、HDR/动态范围、编码、容器和格式 ID。
- 支持同一画质下展开选择其他编码版本。
- 封面优先选取来源可用的高分辨率缩略图，缓存到本机后显示。
- 文件大小来自源站提供的精确/近似值；缺少大小时才根据码率与时长估算，并明确显示「约」。不可靠时显示「大小未知」。
- 分离的视频和音频流在本机通过 FFmpeg stream copy 封装。
- 下载完成后写入 Downloads/VideoDL，下载页保留历史并支持调用系统播放器打开。

## APK 体积优化

Android 手机 APK 只包含 arm64-v8a，FFmpeg 使用 minimal 组件。这样会比同时打包多个 ABI 和 full FFmpeg 更小；不支持 x86_64 模拟器。最终大小由 Actions 构建日志报告。

## 技术分工

| 技术 | 实际职责 |
|---|---|
| Kotlin + Jetpack Compose | Android 原生 UI、格式选择、下载管理 |
| Chaquopy + Python + yt-dlp | 手机本地媒体解析和下载 |
| FFmpegKit minimal | 本机 MP4/MKV 音视频流复制封装 |
| Flask + Python | 可选服务端 API，Android 无需部署 |
| C11/C++17/Rust/Java/SwiftUI/C# | 跨平台客户端和网络/任务核心 |

## 构建 Android

需要 JDK 17、Python 3.13、Android SDK 和 Gradle 8.9：

~~~bash
gradle -p clients/android assembleDebug
~~~

APK 输出：clients/android/app/build/outputs/apk/debug/app-debug.apk

完整说明见 docs/ANDROID.md。仅下载你拥有版权、已获授权或平台允许下载的内容；项目不绕过 DRM 或访问控制。

## License

MIT
