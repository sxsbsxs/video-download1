#ifndef VIDEODL_H
#define VIDEODL_H
/*
 * VideoDL C SDK. C11 + libcurl.
 * Returned JSON strings are heap allocated and must be released with videodl_free().
 */
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct videodl_client videodl_client;

typedef enum videodl_result {
    VIDEODL_OK = 0,
    VIDEODL_INVALID_ARGUMENT = 1,
    VIDEODL_OUT_OF_MEMORY = 2,
    VIDEODL_NETWORK_ERROR = 3,
    VIDEODL_HTTP_ERROR = 4,
    VIDEODL_IO_ERROR = 5,
    VIDEODL_CANCELLED = 6,
    VIDEODL_RESPONSE_TOO_LARGE = 7
} videodl_result;

typedef struct videodl_progress {
    int64_t downloaded_bytes;
    int64_t total_bytes;
    int64_t uploaded_bytes;
    int64_t upload_total_bytes;
} videodl_progress;

/* Return non-zero to cancel the active transfer. */
typedef int (*videodl_progress_callback)(const videodl_progress *progress, void *userdata);

typedef struct videodl_client_options {
    long connect_timeout_ms;  /* 0: 15-second default */
    long info_timeout_ms;     /* 0: 120-second default */
    long download_timeout_ms; /* 0: no total download deadline */
    long low_speed_limit_bps; /* 0: 1 byte/second */
    long low_speed_time_s;    /* 0: 60 seconds */
} videodl_client_options;

/* Remote endpoints require HTTPS. HTTP is allowed only for localhost/loopback. */
videodl_client *videodl_client_create(
    const char *api_base_url,
    const videodl_client_options *options,
    char *error_message,
    size_t error_capacity
);
void videodl_client_destroy(videodl_client *client);

/* Caller owns json_out on success and must release it with videodl_free(). */
videodl_result videodl_health(
    videodl_client *client, char **json_out,
    char *error_message, size_t error_capacity
);
videodl_result videodl_get_info(
    videodl_client *client, const char *video_url, char **json_out,
    char *error_message, size_t error_capacity
);

/*
 * Streams to output_path.part.XXXXXX, fsyncs it, then atomically renames it
 * to output_path. Failures/cancellation never replace the destination with
 * a partial file.
 */
videodl_result videodl_download(
    videodl_client *client,
    const char *video_url,
    const char *quality,
    const char *output_path,
    videodl_progress_callback progress_callback,
    void *progress_userdata,
    char *error_message,
    size_t error_capacity
);

int videodl_url_is_http(const char *url);
int videodl_quality_is_valid(const char *quality);
void videodl_free(void *memory);

#ifdef __cplusplus
}
#endif
#endif
