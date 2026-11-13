# VideoDL 多语言客户端

所有客户端都面向同一个 VideoDL Python API。先启动服务端，再将 API 地址传给客户端。

本地默认地址：http://127.0.0.1:8080

线上部署请填写自己的 HTTPS 地址。不要把未受信任的公共服务地址写死在客户端中。

## TypeScript SDK

~~~bash
cd clients/typescript
npm install
npm run build
~~~

~~~ts
import { VideoDLClient } from "./dist/index.js";

const client = new VideoDLClient("http://127.0.0.1:8080");
const info = await client.info("https://example.com/video");
console.log(info.title, info.formats);
const response = await client.download("https://example.com/video", "best");
~~~

## Go 客户端

~~~bash
cd clients/go
go run . http://127.0.0.1:8080 info "https://example.com/video"
go run . http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

## Rust 客户端

~~~bash
cd clients/rust
cargo run -- http://127.0.0.1:8080 info "https://example.com/video"
cargo run -- http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

## C++ 客户端

依赖 C++17、CMake、libcurl 和 nlohmann-json。

~~~bash
sudo apt-get install cmake g++ libcurl4-openssl-dev nlohmann-json3-dev
cmake -S clients/cpp -B build/cpp
cmake --build build/cpp --config Release
./build/cpp/videodl-cpp http://127.0.0.1:8080 info "https://example.com/video"
./build/cpp/videodl-cpp http://127.0.0.1:8080 download "https://example.com/video" best output.mp4
~~~

## Java 客户端

需要 JDK 17+ 和 Gradle。

~~~bash
gradle -p clients/java run --args='http://127.0.0.1:8080 info https://example.com/video'
gradle -p clients/java run --args='http://127.0.0.1:8080 download https://example.com/video best output.mp4'
~~~

## Kotlin Android 客户端

使用 Android Studio 打开 clients/android。运行后，在应用中填写服务端 URL 和视频 URL。解析后可以查看封面与标题、设置画质，并将文件保存到系统 Downloads/VideoDL 目录。

Android 应用是 API 客户端，不会在手机里重复打包 yt-dlp 或 FFmpeg。

## 使用提醒

- 只对你有权下载的内容使用服务。
- 不要在客户端写入服务器密钥、Cookie 或账号密码。
- 公共服务必须配置 HTTPS、限流、并发限制和合理的文件限制。
