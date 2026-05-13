package com.bililisten.prototype.model;

public record PlaybackProgress(ContentKey key, long positionMs, long durationMs, boolean finished, long updatedAt) {
    public PlaybackProgress {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        if (positionMs < 0L) {
            throw new IllegalArgumentException("positionMs must be >= 0.");
        }
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must be >= 0.");
        }
    }
}

