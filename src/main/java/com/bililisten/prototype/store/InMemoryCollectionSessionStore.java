package com.bililisten.prototype.store;

import com.bililisten.prototype.model.CollectionSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCollectionSessionStore implements CollectionSessionStore {
    private final Map<String, CollectionSession> sessions = new HashMap<>();

    @Override
    public Optional<CollectionSession> loadSession(String collectionId) {
        return Optional.ofNullable(sessions.get(collectionId));
    }

    @Override
    public void saveSession(CollectionSession session) {
        sessions.put(session.collectionId(), session);
    }
}

