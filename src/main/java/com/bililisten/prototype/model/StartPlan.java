package com.bililisten.prototype.model;

public record StartPlan(int startIndex, QueueItem item, long startPositionMs, String reason) {
    public StartPlan {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex must be >= 0.");
        }
        if (item == null) {
            throw new IllegalArgumentException("item must not be null.");
        }
        if (startPositionMs < 0L) {
            throw new IllegalArgumentException("startPositionMs must be >= 0.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank.");
        }
    }
}

