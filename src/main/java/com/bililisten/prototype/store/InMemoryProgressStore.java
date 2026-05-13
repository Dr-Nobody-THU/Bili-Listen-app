package com.bililisten.prototype.store;

import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.PlaybackProgress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProgressStore implements ProgressStore {
    private final Map<String, PlaybackProgress> progressMap = new HashMap<>();

    @Override
    public Optional<PlaybackProgress> loadProgress(ContentKey key) {
        return Optional.ofNullable(progressMap.get(key.asStoreKey()));
    }

    @Override
    public void saveProgress(PlaybackProgress progress) {
        progressMap.put(progress.key().asStoreKey(), progress);
    }
}

