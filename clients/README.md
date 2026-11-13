# VideoDL 应用客户端

所有客户端共用 Python 引擎 API。默认本地地址为 http://127.0.0.1:8080；远程 API 必须使用 HTTPS。

## Android（Kotlin）

使用 Android Studio 打开 clients/android。应用可解析视频信息、查看封面和画质，并将文件保存到 Downloads/VideoDL。

## Apple macOS（SwiftUI）

需要 macOS 13+ 和 Swift 5.9+：

~~~bash
swift build --package-path clients/apple
swift run --package-path clients/apple
~~~

## 桌面（C# / Avalonia）

需要 .NET 8：

~~~bash
dotnet run --project clients/desktop/VideoDL.Desktop.csproj
~~~

Avalonia 可在 Windows、Linux 和 macOS 桌面环境构建。

## Java

需要 JDK 17+ 和 Gradle：

~~~bash
gradle -p clients/java run --args='http://127.0.0.1:8080 info https://example.com/video'
gradle -p clients/java run --args='http://127.0.0.1:8080 download https://example.com/video best output.mp4'
~~~

## C 原生 SDK（C11）

C 语言模块提供独立静态库和 CLI，负责 libcurl 传输、流式写盘、进度回调、取消和临时文件原子落盘。详情见 native/c-client/README.md。

~~~bash
cmake -S native/c-client -B build/c-client
cmake --build build/c-client --parallel
ctest --test-dir build/c-client --output-on-failure
./build/c-client/videodl-c health https://your-engine.example
~~~

## C++

C++ CLI 建立在 VideoDL C SDK 上：C 负责传输和安全文件操作，C++ 负责 JSON 结果呈现及更高层客户端逻辑。

依赖 C++17、CMake、libcurl 和 nlohmann-json：

~~~bash
sudo apt-get install cmake g++ libcurl4-openssl-dev nlohmann-json3-dev
cmake -S clients/cpp -B build/cpp
cmake --build build/cpp --config Release
./build/cpp/videodl-cpp https://your-engine.example health
./build/cpp/videodl-cpp https://your-engine.example info "https://example.com/video"
./build/cpp/videodl-cpp https://your-engine.example download "https://example.com/video" 1080 output.mp4
~~~

## Rust 命令行客户端

~~~bash
cd clients/rust
cargo run -- http://127.0.0.1:8080 info "https://example.com/video"
cargo run -- http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

## Rust 核心库

native/rust-core 提供任务状态转换、进度计算、画质规则和安全文件名处理。当前是可复用核心模块，尚未通过 JNI/FFI 接入 Android 主应用。

~~~bash
cargo test --manifest-path native/rust-core/Cargo.toml
~~~

## 约束

- 不要在客户端硬编码服务器密钥、Cookie 或用户凭据。
- 仅下载你有权保存的内容。
- Android 客户端当前依赖 API 引擎，不是离线内嵌 yt-dlp。
