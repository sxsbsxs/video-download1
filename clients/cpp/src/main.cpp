#include <curl/curl.h>
#include <nlohmann/json.hpp>

#include <cstdio>
#include <iostream>
#include <stdexcept>
#include <string>

using json = nlohmann::json;

namespace {
size_t append_string(void* data, size_t size, size_t count, void* target) {
    const auto bytes = size * count;
    static_cast<std::string*>(target)->append(static_cast<char*>(data), bytes);
    return bytes;
}

size_t write_file(void* data, size_t size, size_t count, void* target) {
    return std::fwrite(data, size, count, static_cast<FILE*>(target));
}

void check_curl(CURLcode code) {
    if (code != CURLE_OK) throw std::runtime_error(curl_easy_strerror(code));
}

long post_json(const std::string& url, const json& payload, std::string& response) {
    CURL* curl = curl_easy_init();
    if (!curl) throw std::runtime_error("无法初始化 libcurl");
    struct curl_slist* headers = nullptr;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    const std::string body = payload.dump();

    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body.c_str());
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, static_cast<long>(body.size()));
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 30L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 0L);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, append_string);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);

    const CURLcode result = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    check_curl(result);
    return status;
}

void print_usage() {
    std::cerr << "用法：videodl-cpp <api-base> <info|download> <video-url> [quality] [output-file]\n";
}
}  // namespace

int main(int argc, char** argv) {
    if (argc < 4) {
        print_usage();
        return 2;
    }
    curl_global_init(CURL_GLOBAL_DEFAULT);
    try {
        std::string base = argv[1];
        while (!base.empty() && base.back() == '/') base.pop_back();
        const std::string action = argv[2];
        const std::string video_url = argv[3];
        const std::string quality = argc > 4 ? argv[4] : "best";

        if (action == "info") {
            std::string response;
            const long status = post_json(base + "/api/info", {{"url", video_url}}, response);
            if (status < 200 || status >= 300) {
                throw std::runtime_error("HTTP " + std::to_string(status) + ": " + response);
            }
            const auto info = json::parse(response);
            std::cout << "标题：" << info.value("title", "未命名视频") << "\n";
            std::cout << "上传者：" << info.value("uploader", "") << "\n";
            std::cout << "可用画质：\n";
            for (const auto& format : info.value("formats", json::array())) {
                std::cout << "  " << format.value("label", "") << " ("
                          << format.value("id", "") << ")\n";
            }
        } else if (action == "download") {
            if (argc < 6) throw std::runtime_error("下载模式还需要指定输出文件名");
            const std::string output_path = argv[5];
            FILE* output = std::fopen(output_path.c_str(), "wb");
            if (!output) throw std::runtime_error("无法创建输出文件");
            CURL* curl = curl_easy_init();
            if (!curl) {
                std::fclose(output);
                throw std::runtime_error("无法初始化 libcurl");
            }
            struct curl_slist* headers = nullptr;
            headers = curl_slist_append(headers, "Content-Type: application/json");
            const std::string body = json{{"url", video_url}, {"quality", quality}}.dump();
            const std::string endpoint = base + "/api/download";
            curl_easy_setopt(curl, CURLOPT_URL, endpoint.c_str());
            curl_easy_setopt(curl, CURLOPT_POST, 1L);
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body.c_str());
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, static_cast<long>(body.size()));
            curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
            curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 30L);
            curl_easy_setopt(curl, CURLOPT_TIMEOUT, 0L);
            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_file);
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, output);
            const CURLcode result = curl_easy_perform(curl);
            long status = 0;
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
            curl_slist_free_all(headers);
            curl_easy_cleanup(curl);
            std::fclose(output);
            check_curl(result);
            if (status < 200 || status >= 300) {
                std::remove(output_path.c_str());
                throw std::runtime_error("下载失败，HTTP " + std::to_string(status)
                    + "（服务端可能返回了 JSON 错误）");
            }
            std::cout << "已保存：" << output_path << "\n";
        } else {
            print_usage();
            curl_global_cleanup();
            return 2;
        }
    } catch (const std::exception& error) {
        std::cerr << "VideoDL: " << error.what() << "\n";
        curl_global_cleanup();
        return 1;
    }
    curl_global_cleanup();
    return 0;
}
