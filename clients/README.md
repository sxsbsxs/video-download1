# VideoDL 应用客户端

所有客户端共用 Python 引擎 API。默认本地地址为 http://127.0.0.1:8080；远程 API 必须使用 HTTPS。

## Android（Kotlin）

使用 Android Studio 打开 clients/android。应用可解析视频信息、查看封面和画质，并将文件保存到 Downloads/VideoDL。

## Java

需要 JDK 17+ 和 Gradle：

~~~bash
gradle -p clients/java run --args='http://127.0.0.1:8080 info https://example.com/video'
gradle -p clients/java run --args='http://127.0.0.1:8080 download https://example.com/video best output.mp4'
~~~

## C++

依赖 C++17、CMake、libcurl 和 nlohmann-json：

~~~bash
sudo apt-get install cmake g++ libcurl4-openssl-dev nlohmann-json3-dev
cmake -S clients/cpp -B build/cpp
cmake --build build/cpp --config Release
./build/cpp/videodl-cpp http://127.0.0.1:8080 info "https://example.com/video"
./build/cpp/videodl-cpp http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

## Rust 命令行客户端

~~~bash
cd clients/rust
cargo run -- http://127.0.0.1:8080 info "https://example.com/video"
cargo run -- http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

Rust 核心库会放在 native/rust-core，提供跨平台的任务状态、进度和画质规则。

## 约束

- 不要在客户端硬编码服务器密钥、Cookie 或用户凭据。
- 仅下载你有权保存的内容。
- Android 客户端当前依赖远程/本地 API 引擎，不是离线内嵌 yt-dlp。
