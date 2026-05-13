package com.bililisten.prototype.model;

import java.util.List;

public record ResolvedAudio(
        ContentKey key,
        String title,
        long durationMs,
        AudioStreamSource selected,
        List<AudioStreamSource> candidates
) {
    public ResolvedAudio {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank.");
        }
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must be >= 0.");
        }
        if (selected == null) {
            throw new IllegalArgumentException("selected must not be null.");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty.");
        }
    }
}
