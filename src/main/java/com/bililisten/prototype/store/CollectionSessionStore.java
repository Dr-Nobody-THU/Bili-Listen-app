package com.bililisten.prototype.store;

import com.bililisten.prototype.model.CollectionSession;

import java.util.Optional;

public interface CollectionSessionStore {
    Optional<CollectionSession> loadSession(String collectionId);

    void saveSession(CollectionSession session);
}

