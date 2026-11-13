#define _POSIX_C_SOURCE 200809L

#include "videodl.h"

#include <inttypes.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static volatile sig_atomic_t interrupted = 0;

static void handle_sigint(int signal_number)
{
    (void)signal_number;
    interrupted = 1;
}

static int print_progress(const videodl_progress *progress, void *userdata)
{
    int *last_percent = (int *)userdata;
    int percent = -1;

    if (interrupted) {
        return 1;
    }
    if (progress->total_bytes > 0) {
        percent = (int)(((double)progress->downloaded_bytes /
                         (double)progress->total_bytes) * 100.0);
        if (percent > 100) {
            percent = 100;
        }
    }

    if (percent >= 0) {
        if (percent != *last_percent) {
            (void)fprintf(stderr, "\r下载进度：%3d%%  (%" PRId64 " / %" PRId64 " bytes)",
                          percent, progress->downloaded_bytes, progress->total_bytes);
            (void)fflush(stderr);
            *last_percent = percent;
        }
    } else if (progress->downloaded_bytes > 0) {
        (void)fprintf(stderr, "\r已下载：%" PRId64 " bytes", progress->downloaded_bytes);
        (void)fflush(stderr);
    }
    return 0;
}

static void print_usage(const char *program)
{
    (void)fprintf(stderr,
        "VideoDL C 客户端（C11）\n\n"
        "用法：\n"
        "  %s health <api-base>\n"
        "  %s info <api-base> <video-url>\n"
        "  %s download <api-base> <video-url> <quality> <output-file>\n\n"
        "quality: best | audio | 144..4320\n"
        "示例：\n"
        "  %s health https://your-engine.example\n"
        "  %s info https://your-engine.example https://video.example/watch/1\n"
        "  %s download https://your-engine.example https://video.example/watch/1 1080 video.mp4\n",
        program, program, program, program, program, program);
}

int main(int argc, char **argv)
{
    char error_message[1024] = {0};
    videodl_client_options options = {0L, 0L, 0L, 0L, 0L};
    videodl_client *client;
    videodl_result result;
    char *response_json = NULL;

    if (argc == 2 && (strcmp(argv[1], "--help") == 0 ||
                      strcmp(argv[1], "-h") == 0)) {
        print_usage(argv[0]);
        return 0;
    }
    if (argc < 3) {
        print_usage(argv[0]);
        return 2;
    }

    client = videodl_client_create(argv[2], &options,
                                   error_message, sizeof(error_message));
    if (client == NULL) {
        (void)fprintf(stderr, "VideoDL: %s\n", error_message);
        return 1;
    }

    if (strcmp(argv[1], "health") == 0 && argc == 3) {
        result = videodl_health(client, &response_json, error_message, sizeof(error_message));
        if (result == VIDEODL_OK) {
            (void)printf("%s\n", response_json);
        }
    } else if (strcmp(argv[1], "info") == 0 && argc == 4) {
        result = videodl_get_info(client, argv[3], &response_json,
                                  error_message, sizeof(error_message));
        if (result == VIDEODL_OK) {
            (void)printf("%s\n", response_json);
        }
    } else if (strcmp(argv[1], "download") == 0 && argc == 6) {
        struct sigaction action;
        int last_percent = -1;
        memset(&action, 0, sizeof(action));
        action.sa_handler = handle_sigint;
        sigemptyset(&action.sa_mask);
        (void)sigaction(SIGINT, &action, NULL);

        result = videodl_download(client, argv[3], argv[4], argv[5],
                                  print_progress, &last_percent,
                                  error_message, sizeof(error_message));
        if (result == VIDEODL_OK) {
            (void)fprintf(stderr, "\n下载完成：%s\n", argv[5]);
        }
    } else {
        print_usage(argv[0]);
        videodl_client_destroy(client);
        return 2;
    }

    videodl_free(response_json);
    videodl_client_destroy(client);

    if (result != VIDEODL_OK) {
        (void)fprintf(stderr, "\nVideoDL: %s\n", error_message);
        return result == VIDEODL_CANCELLED ? 130 : 1;
    }
    return 0;
}
