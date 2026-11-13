# VideoDL 架构

## 单一解析引擎，多语言客户端

~~~text
                    +-------------------------+
                    | Python Flask + yt-dlp   |
                    | /api/health             |
                    | /api/info               |
                    | /api/download           |
                    +------------+------------+
                                 |
                    HTTP JSON / file stream
                                 |
       +-----------+-------------+---------+---------+---------+
       |           |             |         |         |         |
    Browser      Android       Java      C++       Go       Rust
    JS + TS      Kotlin UI     CLI       CLI       CLI       CLI
~~~

## 设计原则

1. yt-dlp 只在 Python 服务端运行，避免每种语言都维护一份站点解析逻辑。
2. 客户端只负责输入、显示解析结果、选择画质和接收文件。
3. HTTP API 是模块边界，各语言可独立升级与发布。
4. TypeScript SDK 为 Web/Node 提供类型定义；Kotlin Android 使用系统下载目录。
5. C++、Go、Rust、Java CLI 便于桌面、自动化与脚本集成。

## API 契约

### POST /api/info

请求体包含 url 字段。成功响应包含 title、thumbnail、duration、uploader 和 formats。

### POST /api/download

请求体包含 url 和 quality 字段。成功时返回附件文件流；失败时返回带 error 字段的 JSON。

## 当前边界

- 解析能力取决于部署版本的 yt-dlp 与目标站点。
- 当前 API 处理公开内容，不支持登录态、Cookie 注入或 DRM 绕过。
- 小规模部署使用内存限流与本地临时目录；多实例部署应迁移到共享队列、共享限流存储和对象存储。
- 下载 API 仍是同步请求；长任务进度、持久任务队列和断点续传属于后续架构升级，不应假称已经实现。
