# VideoDL C SDK and CLI

C11-native client library and command-line application for the VideoDL engine API.

## Responsibilities

The C module is a reusable native transport layer, not another video extractor:

- HTTPS API communication through libcurl with TLS verification enabled.
- Strict API-base and quality validation.
- JSON request escaping without an additional JSON dependency.
- Bounded in-memory response handling for metadata endpoints.
- Streaming downloads directly to disk (no whole-video memory buffering).
- Temporary-file writes followed by atomic rename after a successful response.
- Transfer progress callbacks and caller-driven cancellation.
- Connection timeout, low-speed detection, optional overall download timeout.
- Unit tests for URL policy, quality validation and client construction.

The Python/yt-dlp engine remains the only site extractor. The C SDK consumes the same health, info and download API contracts as the other clients.

## Requirements

- C11 compiler (GCC or Clang)
- CMake 3.16+
- libcurl 7.85+ recommended
- POSIX file APIs (mkstemp, fdopen, fsync, rename); Windows support requires a platform file-operations adapter.

Ubuntu / Debian:

~~~bash
sudo apt-get update
sudo apt-get install -y build-essential cmake pkg-config libcurl4-openssl-dev
~~~

## Build and test

From the repository root:

~~~bash
cmake -S native/c-client -B build/c-client -DCMAKE_BUILD_TYPE=Release
cmake --build build/c-client --parallel
ctest --test-dir build/c-client --output-on-failure
~~~

Optional memory and undefined-behavior sanitizers:

~~~bash
cmake -S native/c-client -B build/c-client-asan \
  -DVIDEODL_ENABLE_SANITIZERS=ON -DCMAKE_BUILD_TYPE=Debug
cmake --build build/c-client-asan --parallel
ctest --test-dir build/c-client-asan --output-on-failure
~~~

## Command line

~~~bash
./build/c-client/videodl-c --help
./build/c-client/videodl-c health https://your-engine.example
./build/c-client/videodl-c info https://your-engine.example https://video.example/watch/123
./build/c-client/videodl-c download https://your-engine.example https://video.example/watch/123 1080 output.mp4
~~~

Valid qualities: best, audio, or a height between 144 and 4320 (for example 1080).

Press Ctrl+C to cancel a download. The incomplete temporary file is removed, and the requested destination is not replaced with a partial file.

## Library API

Include videodl.h and link VideoDL::C (or the built static library and libcurl).

~~~c
#include "videodl.h"

char error[512] = {0};
char *info_json = NULL;
videodl_client_options options = {0};
videodl_client *client = videodl_client_create(
    "https://your-engine.example", &options, error, sizeof(error));

if (client == NULL) {
    fprintf(stderr, "%s\n", error);
    return 1;
}

videodl_result result = videodl_get_info(
    client, "https://video.example/watch/123",
    &info_json, error, sizeof(error));

if (result == VIDEODL_OK) {
    puts(info_json);
    videodl_free(info_json);
}

videodl_client_destroy(client);
~~~

For downloads, pass a progress callback to videodl_download. Return non-zero from the callback to cancel.

## Safety behavior

- Remote engine endpoints must use HTTPS; HTTP is permitted only for localhost/loopback development.
- TLS peer and hostname verification are enabled.
- Redirects are limited to HTTP/HTTPS and at most five hops.
- Metadata response bodies are capped at 4 MiB.
- Downloads stream to a same-directory temporary file and are renamed only after the HTTP response and file write both succeed.
- The SDK does not accept or store login cookies or credentials, and it does not bypass DRM or access controls.
