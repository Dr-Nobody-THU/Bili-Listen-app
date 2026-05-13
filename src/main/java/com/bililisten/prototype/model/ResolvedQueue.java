package com.bililisten.prototype.model;

import java.util.List;

public record ResolvedQueue(String collectionId, String collectionTitle, List<QueueItem> items, int requestedIndex) {
    public ResolvedQueue {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty.");
        }
        if (requestedIndex < 0 || requestedIndex >= items.size()) {
            throw new IllegalArgumentException("requestedIndex out of range: " + requestedIndex);
        }
        if ((collectionTitle == null) != (collectionId == null)) {
            throw new IllegalArgumentException(
                    "collectionId and collectionTitle must either both be null or both be present."
            );
        }

        items = List.copyOf(items);
    }
}

