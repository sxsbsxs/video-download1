# VideoDL 应用架构

## 产品方向

这是应用项目，不是网站项目。用户入口是 Android、Apple 和桌面应用。Python Flask 仅暴露 JSON / 文件流 API，不提供网页、静态资源或网站首页。

~~~text
 Android / Kotlin ─┐
 Apple / SwiftUI ──┼──── HTTPS / JSON / file stream ────> Python Engine API
 Desktop / C# ────┤                                     yt-dlp + FFmpeg
 Java / C++ ──────┤
 C SDK / CLI ─────┘

                  Native modules
          +---------------------------+
          | C: libcurl + streaming I/O|
          | C++: higher-level wrapper |
          | Rust: task & progress core|
          +---------------------------+
~~~

## 语言职责

- Kotlin：Android 原生界面、输入校验、协程、系统下载目录以及后续通知/后台任务。
- Python：站点提取器调用、视频元数据、画质选择、音视频合并及引擎 API。
- C：独立 C11 SDK 和命令行工具；封装 libcurl、JSON 请求转义、流式文件写入、进度回调、取消、超时和安全临时文件落盘。它不重复实现站点提取器。
- C++：更高层原生客户端逻辑；当前 C++ CLI 已链接并复用 C SDK。后续可扩展 NDK / FFmpeg 媒体集成。
- Rust：跨平台任务状态机、进度数据模型、质量规则和安全文件名；目前是独立核心库，尚未接管其他客户端的真实任务。
- Java：JVM 自动化和兼容入口；不是 Android UI 的重复实现。
- Swift：Apple 原生客户端。
- C#：Avalonia 跨平台桌面客户端。

## 关键流程

1. 客户端将公开视频 URL 发送到 /api/info。
2. Python 使用 yt-dlp 读取可用信息和格式。
3. 客户端显示标题、封面及画质选项。
4. 客户端请求 /api/download。
5. Python 使用 yt-dlp 与 FFmpeg 处理，并通过 HTTP 文件流返回结果。
6. Kotlin、Swift、C#、Java、C++ 或 C 客户端保存到系统文件位置。

## C SDK 内部流程

1. 验证 API 地址：远程服务必须是 HTTPS；仅 localhost/loopback 开发地址允许 HTTP。
2. 构造经过 JSON 转义的 API 请求，元数据响应体限制为 4 MiB。
3. 下载响应不会整体读入内存，而是逐块写入目标文件同目录下的临时文件。
4. 回调可读取已下载字节数和总字节数；回调返回非零即可取消。
5. 下载成功后执行 flush、fsync 和原子 rename；网络错误、HTTP 错误、取消或写盘错误都会删除临时文件。

## 重要边界

- 当前下载任务由单个同步 API 请求承载；后端持久任务队列、服务器端实时状态查询和断点续传尚未完成。
- C SDK 的进度反映 HTTP 文件流传输进度，不等同于 yt-dlp 内部解析/转码阶段进度。
- C SDK 当前采用 POSIX 文件操作；Windows 需要单独实现文件操作适配层。
- Rust 核心库尚未通过 JNI/FFI 接入 Kotlin；不能将其描述为已经控制真实下载。
- C SDK 与 C++ 客户端现已共享底层传输实现；Kotlin/Swift/C# 尚未接入 C SDK。
- Android 当前连接 Python 引擎服务；尚非单 APK 完全离线运行。
- Python 的内存限流与临时文件仅适合小规模单实例；多实例部署还需要共享队列、共享限流和对象存储。
- 服务端只处理公开内容，不实现 DRM 或访问控制绕过。
