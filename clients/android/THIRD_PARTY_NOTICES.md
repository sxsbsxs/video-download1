# Android 第三方组件说明

Android APK 内集成以下开源组件：

- **yt-dlp 2026.10.26** — Unlicense。源码与项目说明：https://github.com/yt-dlp/yt-dlp
- **Chaquopy 17.2.0** — Android 内嵌 Python 运行环境。项目与许可证信息：https://chaquo.com/chaquopy/
- **FFmpegKit maintained 8.1.9（min）** — LGPL-3.0。VideoDL 只使用 FFmpeg 的基础封装与流复制能力，不使用 full 包中的可选音视频编码器。项目：https://github.com/ffmpegkit-maintained/ffmpeg

第三方组件继续适用各自许可证；发布 APK 时应随发行包提供对应许可证与必要的 notices。VideoDL 自身代码采用 MIT 许可证。
