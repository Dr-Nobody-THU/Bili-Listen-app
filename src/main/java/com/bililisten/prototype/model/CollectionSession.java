package com.bililisten.prototype.model;

public record CollectionSession(String collectionId, int currentIndex, long updatedAt) {
    public CollectionSession {
        if (collectionId == null || collectionId.isBlank()) {
            throw new IllegalArgumentException("collectionId must not be blank.");
        }
        if (currentIndex < 0) {
            throw new IllegalArgumentException("currentIndex must be >= 0.");
        }
    }
}

