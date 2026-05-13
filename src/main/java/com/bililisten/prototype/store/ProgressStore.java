package com.bililisten.prototype.store;

import com.bililisten.prototype.model.ContentKey;
import com.bililisten.prototype.model.PlaybackProgress;

import java.util.Optional;

public interface ProgressStore {
    Optional<PlaybackProgress> loadProgress(ContentKey key);

    void saveProgress(PlaybackProgress progress);
}
