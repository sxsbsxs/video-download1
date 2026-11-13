#define _POSIX_C_SOURCE 200809L

#include "videodl.h"

#include <curl/curl.h>

#include <errno.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define VIDEODL_DEFAULT_CONNECT_TIMEOUT_MS 15000L
#define VIDEODL_DEFAULT_INFO_TIMEOUT_MS 120000L
#define VIDEODL_DEFAULT_LOW_SPEED_BPS 1L
#define VIDEODL_DEFAULT_LOW_SPEED_TIME_S 60L
#define VIDEODL_JSON_RESPONSE_LIMIT (4U * 1024U * 1024U)
#define VIDEODL_ERROR_PREVIEW_LIMIT 768U

struct videodl_client {
    char *base_url;
    long connect_timeout_ms;
    long info_timeout_ms;
    long download_timeout_ms;
    long low_speed_limit_bps;
    long low_speed_time_s;
};

typedef struct memory_buffer {
    char *data;
    size_t size;
    size_t capacity;
    size_t limit;
    int too_large;
    int out_of_memory;
} memory_buffer;

typedef struct transfer_context {
    FILE *file;
    int write_failed;
    int callback_cancelled;
    videodl_progress_callback progress_callback;
    void *progress_userdata;
} transfer_context;

static atomic_flag global_init_lock = ATOMIC_FLAG_INIT;
static atomic_int global_init_status = ATOMIC_VAR_INIT(0);

static int ensure_curl_global_init(void)
{
    int status = atomic_load_explicit(&global_init_status, memory_order_acquire);
    if (status != 0) {
        return status > 0;
    }

    while (atomic_flag_test_and_set_explicit(&global_init_lock, memory_order_acquire)) {
        /* Initialization is rare; other threads wait for the one-time result. */
    }

    status = atomic_load_explicit(&global_init_status, memory_order_relaxed);
    if (status == 0) {
        status = curl_global_init(CURL_GLOBAL_DEFAULT) == CURLE_OK ? 1 : -1;
        atomic_store_explicit(&global_init_status, status, memory_order_release);
    }

    atomic_flag_clear_explicit(&global_init_lock, memory_order_release);
    return status > 0;
}

static void set_error(char *buffer, size_t capacity, const char *format, ...)
{
    va_list args;
    if (buffer == NULL || capacity == 0U) {
        return;
    }
    va_start(args, format);
    (void)vsnprintf(buffer, capacity, format, args);
    va_end(args);
}

static char *copy_string(const char *value)
{
    size_t length;
    char *copy;

    if (value == NULL) {
        return NULL;
    }
    length = strlen(value);
    if (length == SIZE_MAX) {
        return NULL;
    }
    copy = (char *)malloc(length + 1U);
    if (copy != NULL) {
        memcpy(copy, value, length + 1U);
    }
    return copy;
}

static int get_url_parts(const char *url, char **scheme_out, char **host_out,
                         int *has_userinfo_out)
{
    CURLU *parsed;
    CURLUcode result;
    char *scheme = NULL;
    char *host = NULL;
    char *user = NULL;
    char *password = NULL;
    int ok = 0;

    if (url == NULL || url[0] == '\0' || !ensure_curl_global_init()) {
        return 0;
    }

    parsed = curl_url();
    if (parsed == NULL) {
        return 0;
    }

    result = curl_url_set(parsed, CURLUPART_URL, url, 0);
    if (result != CURLUE_OK) {
        goto cleanup;
    }
    if (curl_url_get(parsed, CURLUPART_SCHEME, &scheme, 0) != CURLUE_OK ||
        curl_url_get(parsed, CURLUPART_HOST, &host, 0) != CURLUE_OK) {
        goto cleanup;
    }

    if (curl_url_get(parsed, CURLUPART_USER, &user, 0) != CURLUE_OK) {
        user = NULL;
    }
    if (curl_url_get(parsed, CURLUPART_PASSWORD, &password, 0) != CURLUE_OK) {
        password = NULL;
    }

    if (scheme_out != NULL) {
        *scheme_out = copy_string(scheme);
        if (*scheme_out == NULL) {
            goto cleanup;
        }
    }
    if (host_out != NULL) {
        *host_out = copy_string(host);
        if (*host_out == NULL) {
            if (scheme_out != NULL) {
                free(*scheme_out);
                *scheme_out = NULL;
            }
            goto cleanup;
        }
    }
    if (has_userinfo_out != NULL) {
        *has_userinfo_out = (user != NULL || password != NULL);
    }
    ok = 1;

cleanup:
    curl_free(scheme);
    curl_free(host);
    curl_free(user);
    curl_free(password);
    curl_url_cleanup(parsed);
    return ok;
}

int videodl_url_is_http(const char *url)
{
    char *scheme = NULL;
    char *host = NULL;
    int has_userinfo = 0;
    int valid = 0;

    if (!get_url_parts(url, &scheme, &host, &has_userinfo)) {
        free(scheme);
        free(host);
        return 0;
    }

    valid = !has_userinfo &&
        (strcmp(scheme, "http") == 0 || strcmp(scheme, "https") == 0);
    free(scheme);
    free(host);
    return valid;
}

static int is_loopback_host(const char *host)
{
    return strcmp(host, "localhost") == 0 ||
           strcmp(host, "127.0.0.1") == 0 ||
           strcmp(host, "::1") == 0 ||
           strcmp(host, "[::1]") == 0;
}

static int is_valid_api_base(const char *url)
{
    char *scheme = NULL;
    char *host = NULL;
    int has_userinfo = 0;
    int valid = 0;

    if (!get_url_parts(url, &scheme, &host, &has_userinfo)) {
        free(scheme);
        free(host);
        return 0;
    }

    valid = !has_userinfo &&
        (strcmp(scheme, "https") == 0 ||
         (strcmp(scheme, "http") == 0 && is_loopback_host(host)));
    free(scheme);
    free(host);
    return valid;
}

int videodl_quality_is_valid(const char *quality)
{
    char *end = NULL;
    long height;

    if (quality == NULL) {
        return 0;
    }
    if (strcmp(quality, "best") == 0 || strcmp(quality, "audio") == 0) {
        return 1;
    }
    if (quality[0] == '\0') {
        return 0;
    }

    for (const char *cursor = quality; *cursor != '\0'; ++cursor) {
        if (*cursor < '0' || *cursor > '9') {
            return 0;
        }
    }

    errno = 0;
    height = strtol(quality, &end, 10);
    return errno == 0 && end != quality && *end == '\0' &&
           height >= 144L && height <= 4320L;
}

void videodl_free(void *memory)
{
    free(memory);
}

videodl_client *videodl_client_create(
    const char *api_base_url,
    const videodl_client_options *options,
    char *error_message,
    size_t error_capacity)
{
    videodl_client *client;
    size_t length;
    long connect_timeout = options != NULL ? options->connect_timeout_ms : 0L;
    long info_timeout = options != NULL ? options->info_timeout_ms : 0L;
    long download_timeout = options != NULL ? options->download_timeout_ms : 0L;
    long low_speed_limit = options != NULL ? options->low_speed_limit_bps : 0L;
    long low_speed_time = options != NULL ? options->low_speed_time_s : 0L;

    if (!is_valid_api_base(api_base_url)) {
        set_error(error_message, error_capacity,
                  "API 地址必须是 HTTPS；仅 localhost 可使用 HTTP。");
        return NULL;
    }

    if (connect_timeout < 0L || info_timeout < 0L ||
        download_timeout < 0L || low_speed_limit < 0L ||
        low_speed_time < 0L) {
        set_error(error_message, error_capacity, "超时和限速参数不能为负数。");
        return NULL;
    }

    if (!ensure_curl_global_init()) {
        set_error(error_message, error_capacity, "libcurl 全局初始化失败。");
        return NULL;
    }

    client = (videodl_client *)calloc(1U, sizeof(*client));
    if (client == NULL) {
        set_error(error_message, error_capacity, "内存不足，无法创建客户端。");
        return NULL;
    }

    length = strlen(api_base_url);
    while (length > 0U && api_base_url[length - 1U] == '/') {
        --length;
    }
    client->base_url = (char *)malloc(length + 1U);
    if (client->base_url == NULL) {
        free(client);
        set_error(error_message, error_capacity, "内存不足，无法保存 API 地址。");
        return NULL;
    }
    memcpy(client->base_url, api_base_url, length);
    client->base_url[length] = '\0';

    client->connect_timeout_ms = connect_timeout > 0L
        ? connect_timeout : VIDEODL_DEFAULT_CONNECT_TIMEOUT_MS;
    client->info_timeout_ms = info_timeout > 0L
        ? info_timeout : VIDEODL_DEFAULT_INFO_TIMEOUT_MS;
    client->download_timeout_ms = download_timeout;
    client->low_speed_limit_bps = low_speed_limit > 0L
        ? low_speed_limit : VIDEODL_DEFAULT_LOW_SPEED_BPS;
    client->low_speed_time_s = low_speed_time > 0L
        ? low_speed_time : VIDEODL_DEFAULT_LOW_SPEED_TIME_S;

    if (error_message != NULL && error_capacity > 0U) {
        error_message[0] = '\0';
    }
    return client;
}

void videodl_client_destroy(videodl_client *client)
{
    if (client == NULL) {
        return;
    }
    free(client->base_url);
    free(client);
    /* libcurl global state stays alive for process lifetime so parallel
       client instances cannot invalidate one another. */
}

static size_t memory_write_callback(char *data, size_t size, size_t count, void *userdata)
{
    memory_buffer *buffer = (memory_buffer *)userdata;
    size_t bytes;
    size_t required;
    size_t new_capacity;
    char *new_data;

    if (size != 0U && count > SIZE_MAX / size) {
        buffer->out_of_memory = 1;
        return 0U;
    }
    bytes = size * count;
    if (bytes > buffer->limit - buffer->size) {
        buffer->too_large = 1;
        return 0U;
    }
    required = buffer->size + bytes + 1U;

    if (required > buffer->capacity) {
        new_capacity = buffer->capacity == 0U ? 4096U : buffer->capacity;
        while (new_capacity < required) {
            if (new_capacity > buffer->limit / 2U) {
                new_capacity = buffer->limit + 1U;
                break;
            }
            new_capacity *= 2U;
        }
        new_data = (char *)realloc(buffer->data, new_capacity);
        if (new_data == NULL) {
            buffer->out_of_memory = 1;
            return 0U;
        }
        buffer->data = new_data;
        buffer->capacity = new_capacity;
    }

    if (bytes > 0U) {
        memcpy(buffer->data + buffer->size, data, bytes);
    }
    buffer->size += bytes;
    buffer->data[buffer->size] = '\0';
    return bytes;
}

static void configure_easy_handle(CURL *curl, const videodl_client *client,
                                  const char *url, char *curl_error)
{
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, curl_error);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, client->connect_timeout_ms);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_MAXREDIRS, 5L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "VideoDL-C/0.1");
#if LIBCURL_VERSION_NUM >= 0x075500
    curl_easy_setopt(curl, CURLOPT_PROTOCOLS_STR, "http,https");
    curl_easy_setopt(curl, CURLOPT_REDIR_PROTOCOLS_STR, "http,https");
#else
    curl_easy_setopt(curl, CURLOPT_PROTOCOLS, (long)(CURLPROTO_HTTP | CURLPROTO_HTTPS));
    curl_easy_setopt(curl, CURLOPT_REDIR_PROTOCOLS, (long)(CURLPROTO_HTTP | CURLPROTO_HTTPS));
#endif
}

static char *make_endpoint(const videodl_client *client, const char *path)
{
    size_t base_length = strlen(client->base_url);
    size_t path_length = strlen(path);
    char *endpoint;

    if (base_length > SIZE_MAX - path_length - 1U) {
        return NULL;
    }
    endpoint = (char *)malloc(base_length + path_length + 1U);
    if (endpoint == NULL) {
        return NULL;
    }
    memcpy(endpoint, client->base_url, base_length);
    memcpy(endpoint + base_length, path, path_length + 1U);
    return endpoint;
}

static char *json_escape_string(const char *input)
{
    static const char hex[] = "0123456789abcdef";
    size_t length = strlen(input);
    char *output;
    size_t capacity;
    size_t used = 0U;

    if (length > (SIZE_MAX - 3U) / 6U) {
        return NULL;
    }
    capacity = length * 6U + 3U;
    output = (char *)malloc(capacity);
    if (output == NULL) {
        return NULL;
    }

    output[used++] = '"';
    for (const unsigned char *cursor = (const unsigned char *)input;
         *cursor != '\0'; ++cursor) {
        unsigned char value = *cursor;
        if (value == '"' || value == '\\') {
            output[used++] = '\\';
            output[used++] = (char)value;
        } else if (value < 0x20U) {
            output[used++] = '\\';
            output[used++] = 'u';
            output[used++] = '0';
            output[used++] = '0';
            output[used++] = hex[value >> 4U];
            output[used++] = hex[value & 0x0fU];
        } else {
            output[used++] = (char)value;
        }
    }
    output[used++] = '"';
    output[used] = '\0';
    return output;
}

static char *make_download_json(const char *video_url, const char *quality)
{
    char *escaped_url = json_escape_string(video_url);
    char *escaped_quality = json_escape_string(quality);
    char *body;
    size_t required;

    if (escaped_url == NULL || escaped_quality == NULL) {
        free(escaped_url);
        free(escaped_quality);
        return NULL;
    }

    required = strlen(escaped_url) + strlen(escaped_quality) + 22U;
    body = (char *)malloc(required);
    if (body != NULL) {
        (void)snprintf(body, required, "{\"url\":%s,\"quality\":%s}",
                       escaped_url, escaped_quality);
    }
    free(escaped_url);
    free(escaped_quality);
    return body;
}

static void set_http_error(char *error_message, size_t error_capacity,
                           long status, const char *body)
{
    if (body != NULL && body[0] != '\0') {
        size_t preview = strlen(body);
        if (preview > VIDEODL_ERROR_PREVIEW_LIMIT) {
            preview = VIDEODL_ERROR_PREVIEW_LIMIT;
        }
        set_error(error_message, error_capacity,
                  "HTTP %ld：服务端返回：%.*s", status, (int)preview, body);
    } else {
        set_error(error_message, error_capacity, "HTTP %ld：服务端请求失败。", status);
    }
}

static videodl_result request_json(videodl_client *client, const char *path,
                                   const char *body, char **json_out,
                                   char *error_message, size_t error_capacity)
{
    CURL *curl = NULL;
    CURLcode result;
    long status = 0L;
    char curl_error[CURL_ERROR_SIZE] = {0};
    char *endpoint = NULL;
    struct curl_slist *headers = NULL;
    memory_buffer response = {NULL, 0U, 0U, VIDEODL_JSON_RESPONSE_LIMIT, 0, 0};
    videodl_result return_code = VIDEODL_NETWORK_ERROR;

    if (json_out != NULL) {
        *json_out = NULL;
    }
    if (client == NULL || path == NULL || json_out == NULL) {
        set_error(error_message, error_capacity, "客户端或输出参数无效。");
        return VIDEODL_INVALID_ARGUMENT;
    }

    endpoint = make_endpoint(client, path);
    if (endpoint == NULL) {
        set_error(error_message, error_capacity, "内存不足，无法创建 API 地址。");
        return VIDEODL_OUT_OF_MEMORY;
    }
    curl = curl_easy_init();
    if (curl == NULL) {
        set_error(error_message, error_capacity, "无法创建 libcurl 请求句柄。");
        return_code = VIDEODL_OUT_OF_MEMORY;
        goto cleanup;
    }

    configure_easy_handle(curl, client, endpoint, curl_error);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, client->info_timeout_ms);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, memory_write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);

    if (body != NULL) {
        headers = curl_slist_append(headers, "Content-Type: application/json");
        if (headers == NULL) {
            set_error(error_message, error_capacity, "无法分配 HTTP 请求头。");
            return_code = VIDEODL_OUT_OF_MEMORY;
            goto cleanup;
        }
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(body));
    } else {
        curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    }

    result = curl_easy_perform(curl);
    if (response.data == NULL) {
        response.data = copy_string("");
        if (response.data == NULL) {
            set_error(error_message, error_capacity, "内存不足，无法保存服务端响应。");
            return_code = VIDEODL_OUT_OF_MEMORY;
            goto cleanup;
        }
    }

    if (result != CURLE_OK) {
        if (response.too_large) {
            set_error(error_message, error_capacity, "服务端响应超过 4 MiB 安全上限。");
            return_code = VIDEODL_RESPONSE_TOO_LARGE;
        } else if (response.out_of_memory) {
            set_error(error_message, error_capacity, "内存不足，无法接收服务端响应。");
            return_code = VIDEODL_OUT_OF_MEMORY;
        } else {
            set_error(error_message, error_capacity, "%s",
                      curl_error[0] != '\0' ? curl_error : curl_easy_strerror(result));
            return_code = VIDEODL_NETWORK_ERROR;
        }
        goto cleanup;
    }

    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    if (status < 200L || status >= 300L) {
        set_http_error(error_message, error_capacity, status, response.data);
        return_code = VIDEODL_HTTP_ERROR;
        goto cleanup;
    }

    *json_out = response.data;
    response.data = NULL;
    return_code = VIDEODL_OK;

cleanup:
    if (headers != NULL) {
        curl_slist_free_all(headers);
    }
    if (curl != NULL) {
        curl_easy_cleanup(curl);
    }
    free(endpoint);
    free(response.data);
    return return_code;
}

videodl_result videodl_health(videodl_client *client, char **json_out,
                              char *error_message, size_t error_capacity)
{
    return request_json(client, "/api/health", NULL, json_out,
                        error_message, error_capacity);
}

videodl_result videodl_get_info(videodl_client *client, const char *video_url,
                                char **json_out, char *error_message,
                                size_t error_capacity)
{
    char *body;
    videodl_result result;

    if (video_url == NULL || !videodl_url_is_http(video_url)) {
        if (json_out != NULL) {
            *json_out = NULL;
        }
        set_error(error_message, error_capacity,
                  "视频链接必须是有效的 HTTP 或 HTTPS URL。");
        return VIDEODL_INVALID_ARGUMENT;
    }

    body = make_download_json(video_url, "best");
    if (body == NULL) {
        set_error(error_message, error_capacity, "内存不足，无法创建解析请求。");
        return VIDEODL_OUT_OF_MEMORY;
    }
    result = request_json(client, "/api/info", body, json_out,
                          error_message, error_capacity);
    free(body);
    return result;
}

static size_t file_write_callback(char *data, size_t size, size_t count, void *userdata)
{
    transfer_context *context = (transfer_context *)userdata;
    size_t bytes;

    if (size != 0U && count > SIZE_MAX / size) {
        context->write_failed = 1;
        return 0U;
    }
    bytes = size * count;
    if (bytes > 0U && fwrite(data, 1U, bytes, context->file) != bytes) {
        context->write_failed = 1;
        return 0U;
    }
    return bytes;
}

static int transfer_progress_callback(void *userdata, curl_off_t download_total,
                                      curl_off_t download_now, curl_off_t upload_total,
                                      curl_off_t upload_now)
{
    transfer_context *context = (transfer_context *)userdata;
    videodl_progress progress;

    if (context->progress_callback == NULL) {
        return 0;
    }
    progress.downloaded_bytes = (int64_t)download_now;
    progress.total_bytes = (int64_t)download_total;
    progress.uploaded_bytes = (int64_t)upload_now;
    progress.upload_total_bytes = (int64_t)upload_total;

    if (context->progress_callback(&progress, context->progress_userdata) != 0) {
        context->callback_cancelled = 1;
        return 1;
    }
    return 0;
}

static void remove_temp_file(const char *path, FILE *file, int fd)
{
    if (file != NULL) {
        (void)fclose(file);
    } else if (fd >= 0) {
        (void)close(fd);
    }
    if (path != NULL) {
        (void)unlink(path);
    }
}

videodl_result videodl_download(
    videodl_client *client,
    const char *video_url,
    const char *quality,
    const char *output_path,
    videodl_progress_callback progress_callback,
    void *progress_userdata,
    char *error_message,
    size_t error_capacity)
{
    CURL *curl = NULL;
    CURLcode transfer_result;
    long status = 0L;
    char curl_error[CURL_ERROR_SIZE] = {0};
    char *endpoint = NULL;
    char *body = NULL;
    char *temp_path = NULL;
    size_t temp_capacity;
    int fd = -1;
    FILE *file = NULL;
    struct curl_slist *headers = NULL;
    transfer_context context;
    videodl_result return_code = VIDEODL_NETWORK_ERROR;
    struct stat output_stat;

    memset(&context, 0, sizeof(context));

    if (client == NULL || video_url == NULL || quality == NULL ||
        output_path == NULL || output_path[0] == '\0') {
        set_error(error_message, error_capacity, "下载参数无效。");
        return VIDEODL_INVALID_ARGUMENT;
    }
    if (!videodl_url_is_http(video_url)) {
        set_error(error_message, error_capacity,
                  "视频链接必须是有效的 HTTP 或 HTTPS URL。");
        return VIDEODL_INVALID_ARGUMENT;
    }
    if (!videodl_quality_is_valid(quality)) {
        set_error(error_message, error_capacity,
                  "画质必须是 best、audio 或 144 到 4320 之间的分辨率。");
        return VIDEODL_INVALID_ARGUMENT;
    }
    if (stat(output_path, &output_stat) == 0 && S_ISDIR(output_stat.st_mode)) {
        set_error(error_message, error_capacity, "输出路径指向一个目录。");
        return VIDEODL_INVALID_ARGUMENT;
    }

    endpoint = make_endpoint(client, "/api/download");
    body = make_download_json(video_url, quality);
    if (endpoint == NULL || body == NULL) {
        set_error(error_message, error_capacity, "内存不足，无法创建下载请求。");
        return_code = VIDEODL_OUT_OF_MEMORY;
        goto cleanup;
    }

    if (strlen(output_path) > SIZE_MAX - 16U) {
        set_error(error_message, error_capacity, "输出路径过长。");
        return_code = VIDEODL_INVALID_ARGUMENT;
        goto cleanup;
    }
    temp_capacity = strlen(output_path) + 16U;
    temp_path = (char *)malloc(temp_capacity);
    if (temp_path == NULL) {
        set_error(error_message, error_capacity, "内存不足，无法创建临时文件路径。");
        return_code = VIDEODL_OUT_OF_MEMORY;
        goto cleanup;
    }
    (void)snprintf(temp_path, temp_capacity, "%s.part.XXXXXX", output_path);
    fd = mkstemp(temp_path);
    if (fd < 0) {
        set_error(error_message, error_capacity, "无法创建临时下载文件：%s", strerror(errno));
        return_code = VIDEODL_IO_ERROR;
        goto cleanup;
    }
    file = fdopen(fd, "wb");
    if (file == NULL) {
        set_error(error_message, error_capacity, "无法打开临时下载文件：%s", strerror(errno));
        (void)close(fd);
        fd = -1;
        (void)unlink(temp_path);
        return_code = VIDEODL_IO_ERROR;
        goto cleanup;
    }
    fd = -1;
    context.file = file;
    context.progress_callback = progress_callback;
    context.progress_userdata = progress_userdata;

    curl = curl_easy_init();
    if (curl == NULL) {
        set_error(error_message, error_capacity, "无法创建 libcurl 请求句柄。");
        return_code = VIDEODL_OUT_OF_MEMORY;
        goto cleanup;
    }

    configure_easy_handle(curl, client, endpoint, curl_error);
    curl_easy_setopt(curl, CURLOPT_POST, 1L);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(body));
    headers = curl_slist_append(headers, "Content-Type: application/json");
    if (headers == NULL) {
        set_error(error_message, error_capacity, "无法分配 HTTP 请求头。");
        return_code = VIDEODL_OUT_OF_MEMORY;
        goto cleanup;
    }
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, file_write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &context);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
    curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, transfer_progress_callback);
    curl_easy_setopt(curl, CURLOPT_XFERINFODATA, &context);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, client->low_speed_limit_bps);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, client->low_speed_time_s);
    if (client->download_timeout_ms > 0L) {
        curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, client->download_timeout_ms);
    }

    transfer_result = curl_easy_perform(curl);
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);

    if (fflush(file) != 0) {
        context.write_failed = 1;
    }
    if (fsync(fileno(file)) != 0) {
        context.write_failed = 1;
    }
    if (fclose(file) != 0) {
        context.write_failed = 1;
    }
    file = NULL;

    if (context.callback_cancelled) {
        set_error(error_message, error_capacity, "下载已取消。");
        return_code = VIDEODL_CANCELLED;
        goto cleanup;
    }
    if (context.write_failed) {
        set_error(error_message, error_capacity, "写入下载文件失败：%s", strerror(errno));
        return_code = VIDEODL_IO_ERROR;
        goto cleanup;
    }
    if (transfer_result != CURLE_OK) {
        set_error(error_message, error_capacity, "%s",
                  curl_error[0] != '\0' ? curl_error : curl_easy_strerror(transfer_result));
        return_code = VIDEODL_NETWORK_ERROR;
        goto cleanup;
    }
    if (status < 200L || status >= 300L) {
        FILE *error_file = fopen(temp_path, "rb");
        char preview[VIDEODL_ERROR_PREVIEW_LIMIT + 1U];
        size_t bytes = 0U;
        if (error_file != NULL) {
            bytes = fread(preview, 1U, VIDEODL_ERROR_PREVIEW_LIMIT, error_file);
            (void)fclose(error_file);
        }
        preview[bytes] = '\0';
        set_http_error(error_message, error_capacity, status, preview);
        return_code = VIDEODL_HTTP_ERROR;
        goto cleanup;
    }
    if (stat(temp_path, &output_stat) != 0 || output_stat.st_size <= 0) {
        set_error(error_message, error_capacity, "服务端返回了空文件。");
        return_code = VIDEODL_IO_ERROR;
        goto cleanup;
    }
    if (rename(temp_path, output_path) != 0) {
        set_error(error_message, error_capacity,
                  "无法将下载文件保存到目标位置：%s", strerror(errno));
        return_code = VIDEODL_IO_ERROR;
        goto cleanup;
    }

    set_error(error_message, error_capacity, "");
    return_code = VIDEODL_OK;

cleanup:
    if (headers != NULL) {
        curl_slist_free_all(headers);
    }
    if (curl != NULL) {
        curl_easy_cleanup(curl);
    }
    remove_temp_file(temp_path, file, fd);
    free(temp_path);
    free(endpoint);
    free(body);
    return return_code;
}
