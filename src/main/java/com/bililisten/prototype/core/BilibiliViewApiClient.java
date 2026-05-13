package com.bililisten.prototype.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class BilibiliViewApiClient implements ViewDataSource {
    private static final String VIEW_API = "https://api.bilibili.com/x/web-interface/view?bvid=";

    @Override
    public Map<String, Object> fetchView(String bvid) {
        HttpURLConnection connection = null;
        try {
            String encodedBvid = URLEncoder.encode(bvid, StandardCharsets.UTF_8);
            URL url = URI.create(VIEW_API + encodedBvid).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Referer", "https://www.bilibili.com/");

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readBody(stream);
            if (statusCode != 200) {
                throw new IllegalStateException("Bilibili view API returned HTTP " + statusCode + ": " + body);
            }

            Map<String, Object> root = Jsons.asObject(SimpleJsonParser.parse(body), "view response");
            long code = Jsons.optLong(root, "code", -1);
            if (code != 0L) {
                String message = Jsons.optString(root, "message");
                throw new IllegalStateException("Bilibili view API failed: code=" + code + ", message=" + message);
            }
            return Jsons.getObject(root, "data");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Bilibili view API.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
