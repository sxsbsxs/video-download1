#include "videodl.h"
#include <nlohmann/json.hpp>

#include <cstdint>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

using json = nlohmann::json;

namespace {

class VideoDLClient {
public:
    explicit VideoDLClient(const std::string& api_base) {
        char error[512] = {0};
        const videodl_client_options options = {0L, 0L, 0L, 0L, 0L};
        client_ = videodl_client_create(api_base.c_str(), &options, error, sizeof(error));
        if (client_ == nullptr) {
            throw std::runtime_error(error[0] != '\0' ? error : "无法创建 VideoDL 客户端");
        }
    }

    ~VideoDLClient() {
        videodl_client_destroy(client_);
    }

    VideoDLClient(const VideoDLClient&) = delete;
    VideoDLClient& operator=(const VideoDLClient&) = delete;

    void health() const {
        char error[1024] = {0};
        char* raw = nullptr;
        const auto result = videodl_health(client_, &raw, error, sizeof(error));
        std::unique_ptr<char, decltype(&videodl_free)> response(raw, &videodl_free);
        check(result, error);
        std::cout << (response ? response.get() : "{}") << '\n';
    }

    void info(const std::string& video_url) const {
        char error[1024] = {0};
        char* raw = nullptr;
        const auto result = videodl_get_info(
            client_, video_url.c_str(), &raw, error, sizeof(error));
        std::unique_ptr<char, decltype(&videodl_free)> response(raw, &videodl_free);
        check(result, error);

        const auto info = json::parse(response.get());
        std::cout << "标题：" << info.value("title", "未命名视频") << '\n';
        std::cout << "上传者：" << info.value("uploader", "") << '\n';
        std::cout << "可用画质：\n";
        for (const auto& format : info.value("formats", json::array())) {
            std::cout << "  " << format.value("label", "") << " ("
                      << format.value("id", "") << ")\n";
        }
    }

    void download(const std::string& video_url,
                  const std::string& quality,
                  const std::string& output_path) const {
        char error[1024] = {0};
        int last_percent = -1;
        const auto result = videodl_download(
            client_, video_url.c_str(), quality.c_str(), output_path.c_str(),
            &VideoDLClient::progress, &last_percent, error, sizeof(error));
        check(result, error);
        std::cerr << "\n下载完成：" << output_path << '\n';
    }

private:
    videodl_client* client_ = nullptr;

    static int progress(const videodl_progress* value, void* userdata) {
        auto* last_percent = static_cast<int*>(userdata);
        if (value->total_bytes > 0) {
            const int percent = static_cast<int>(
                (static_cast<double>(value->downloaded_bytes) /
                 static_cast<double>(value->total_bytes)) * 100.0);
            if (percent != *last_percent) {
                std::cerr << "\r下载进度：" << percent << "% ("
                          << value->downloaded_bytes << " / "
                          << value->total_bytes << " bytes)" << std::flush;
                *last_percent = percent;
            }
        } else if (value->downloaded_bytes > 0) {
            std::cerr << "\r已下载：" << value->downloaded_bytes << " bytes" << std::flush;
        }
        return 0;
    }

    static void check(videodl_result result, const char* error) {
        if (result != VIDEODL_OK) {
            throw std::runtime_error(error != nullptr && error[0] != '\0'
                ? error : "VideoDL 请求失败");
        }
    }
};

void print_usage() {
    std::cerr
        << "用法：videodl-cpp <api-base> <health|info|download> "
        << "[video-url] [quality] [output-file]\n"
        << "示例：\n"
        << "  videodl-cpp https://your-engine.example health\n"
        << "  videodl-cpp https://your-engine.example info https://video.example/watch/1\n"
        << "  videodl-cpp https://your-engine.example download "
        << "https://video.example/watch/1 1080 video.mp4\n";
}

} // namespace

int main(int argc, char** argv) {
    if (argc == 2 && (std::string(argv[1]) == "--help" ||
                      std::string(argv[1]) == "-h")) {
        print_usage();
        return 0;
    }
    if (argc < 3) {
        print_usage();
        return 2;
    }

    try {
        VideoDLClient client(argv[1]);
        const std::string action = argv[2];

        if (action == "health" && argc == 3) {
            client.health();
        } else if (action == "info" && argc == 4) {
            client.info(argv[3]);
        } else if (action == "download" && argc == 6) {
            client.download(argv[3], argv[4], argv[5]);
        } else {
            print_usage();
            return 2;
        }
    } catch (const std::exception& error) {
        std::cerr << "VideoDL: " << error.what() << '\n';
        return 1;
    }
    return 0;
}
