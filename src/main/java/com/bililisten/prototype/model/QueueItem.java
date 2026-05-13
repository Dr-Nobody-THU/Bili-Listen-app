package com.bililisten.prototype.model;

public record QueueItem(
        ContentKey key,
        String title,
        long durationMs,
        int pageNumber,
        int orderInCollection,
        String collectionId,
        String collectionTitle,
        String coverUrl
) {
    public QueueItem {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank.");
        }
        if (durationMs < 0L) {
            throw new IllegalArgumentException("durationMs must be >= 0.");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1.");
        }
        if (orderInCollection < 0) {
            throw new IllegalArgumentException("orderInCollection must be >= 0.");
        }
        if ((collectionTitle == null) != (collectionId == null)) {
            throw new IllegalArgumentException(
                    "collectionId and collectionTitle must either both be null or both be present."
            );
        }
    }
}
