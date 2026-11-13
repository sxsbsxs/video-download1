package io.videodl.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            usage();
            System.exit(2);
        }
        String base = args[0].replaceAll("/+$", "");
        String action = args[1];
        String videoUrl = args[2];
        String quality = args.length > 3 ? args[3] : "best";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        if ("info".equals(action)) {
            HttpResponse<String> response = client.send(
                    post(base + "/api/info", Map.of("url", videoUrl)),
                    HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            JsonNode info = JSON.readTree(response.body());
            System.out.println("标题：" + info.path("title").asText("未命名视频"));
            System.out.println("上传者：" + info.path("uploader").asText(""));
            for (JsonNode format : info.path("formats")) {
                System.out.printf("画质：%s (%s)%n",
                        format.path("label").asText(),
                        format.path("id").asText());
            }
        } else if ("download".equals(action)) {
            if (args.length < 5) {
                usage();
                System.exit(2);
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("url", videoUrl);
            payload.put("quality", quality);
            HttpResponse<InputStream> response = client.send(
                    post(base + "/api/download", payload),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error;
                try (InputStream input = response.body()) {
                    error = new String(input.readAllBytes());
                }
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + error);
            }
            Path output = Path.of(args[4]);
            try (InputStream input = response.body()) {
                Files.copy(input, output);
            }
            System.out.println("已保存：" + output.toAbsolutePath());
        } else {
            usage();
            System.exit(2);
        }
    }

    private static HttpRequest post(String url, Object payload) throws Exception {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                .build();
    }

    private static void ensureSuccess(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + body);
        }
    }

    private static void usage() {
        System.err.println("用法：videodl-java <api-base> <info|download> <video-url> [quality] [output-file]");
    }
}
