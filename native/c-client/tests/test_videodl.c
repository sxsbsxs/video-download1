#include "videodl.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

static void test_quality_values(void)
{
    assert(videodl_quality_is_valid("best"));
    assert(videodl_quality_is_valid("audio"));
    assert(videodl_quality_is_valid("144"));
    assert(videodl_quality_is_valid("720"));
    assert(videodl_quality_is_valid("1080"));
    assert(videodl_quality_is_valid("4320"));

    assert(!videodl_quality_is_valid(NULL));
    assert(!videodl_quality_is_valid(""));
    assert(!videodl_quality_is_valid("143"));
    assert(!videodl_quality_is_valid("4321"));
    assert(!videodl_quality_is_valid("1080p"));
    assert(!videodl_quality_is_valid("-720"));
    assert(!videodl_quality_is_valid("1e3"));
    assert(!videodl_quality_is_valid("999999999999999999999"));
}

static void test_video_urls(void)
{
    assert(videodl_url_is_http("https://example.com/video?id=1"));
    assert(videodl_url_is_http("http://example.com/watch/2"));

    assert(!videodl_url_is_http(NULL));
    assert(!videodl_url_is_http(""));
    assert(!videodl_url_is_http("ftp://example.com/file"));
    assert(!videodl_url_is_http("file:///tmp/video.mp4"));
    assert(!videodl_url_is_http("https://user:pass@example.com/video"));
    assert(!videodl_url_is_http("not-a-url"));
}

static void test_client_base_url_policy(void)
{
    char error[256] = {0};
    videodl_client_options options = {0L, 0L, 0L, 0L, 0L};
    videodl_client *client;

    client = videodl_client_create("https://engine.example/api/", &options,
                                   error, sizeof(error));
    assert(client != NULL);
    videodl_client_destroy(client);

    client = videodl_client_create("http://127.0.0.1:8080", &options,
                                   error, sizeof(error));
    assert(client != NULL);
    videodl_client_destroy(client);

    client = videodl_client_create("http://engine.example", &options,
                                   error, sizeof(error));
    assert(client == NULL);
    assert(strstr(error, "HTTPS") != NULL);
}

int main(void)
{
    test_quality_values();
    test_video_urls();
    test_client_base_url_policy();
    (void)puts("VideoDL C unit tests: all tests passed");
    return 0;
}
