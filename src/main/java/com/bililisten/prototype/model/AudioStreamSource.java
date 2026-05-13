package com.bililisten.prototype.model;

import java.util.List;

public record AudioStreamSource(
        int id,
        String url,
        List<String> backupUrls,
        long bandwidth,
        String codec,
        String mimeType,
        AudioStreamKind kind
) {
    public AudioStreamSource {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0.");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank.");
        }
        backupUrls = backupUrls == null ? List.of() : List.copyOf(backupUrls);
        if (bandwidth < 0L) {
            throw new IllegalArgumentException("bandwidth must be >= 0.");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null.");
        }
    }
}

