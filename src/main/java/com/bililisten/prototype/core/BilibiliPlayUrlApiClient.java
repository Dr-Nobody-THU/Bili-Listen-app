package com.bililisten.prototype.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class BilibiliPlayUrlApiClient implements PlayUrlDataSource {
    private static final String PLAY_URL_API =
            "https://api.bilibili.com/x/player/playurl?fnval=4048&fnver=0&fourk=1&bvid=%s&cid=%d";

    private final HttpClient httpClient;

    public BilibiliPlayUrlApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public Map<String, Object> fetchPlayUrl(String bvid, long cid) {
        String uri = PLAY_URL_API.formatted(URLEncoder.encode(bvid, StandardCharsets.UTF_8), cid);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com/")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Bilibili playurl API returned HTTP " + response.statusCode());
            }

            Map<String, Object> root = Jsons.asObject(SimpleJsonParser.parse(response.body()), "playurl response");
            long code = Jsons.optLong(root, "code", -1L);
            if (code != 0L) {
                String message = Jsons.optString(root, "message");
                throw new IllegalStateException("Bilibili playurl API failed: code=" + code + ", message=" + message);
            }

            return Jsons.getObject(root, "data");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Bilibili playurl API.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Bilibili playurl API.", exception);
        }
    }
}

