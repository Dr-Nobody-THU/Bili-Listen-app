package com.bililisten.prototype.core;

import com.bililisten.prototype.model.CollectionSession;
import com.bililisten.prototype.model.PlaybackProgress;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.ResumePolicy;
import com.bililisten.prototype.model.StartPlan;
import com.bililisten.prototype.store.ProgressStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlaybackPlanner {
    private static final long FINISHED_THRESHOLD_MS = 3_000L;

    public StartPlan planStart(
            ResolvedQueue queue,
            Optional<CollectionSession> savedSession,
            ProgressStore progressStore,
            ResumePolicy resumePolicy
    ) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(savedSession, "savedSession");
        Objects.requireNonNull(progressStore, "progressStore");
        Objects.requireNonNull(resumePolicy, "resumePolicy");

        int startIndex = queue.requestedIndex();
        String reason = "requested item";

        if (resumePolicy == ResumePolicy.PREFER_SAVED_COLLECTION_POSITION
                && queue.collectionId() != null
                && savedSession.isPresent()
                && queue.collectionId().equals(savedSession.get().collectionId())) {
            startIndex = clampIndex(savedSession.get().currentIndex(), queue.items().size());
            reason = "saved collection position";
        }

        int normalizedIndex = advancePastFinished(queue.items(), startIndex, progressStore);
        if (normalizedIndex != startIndex) {
            reason = "next unfinished item";
        }

        QueueItem item = queue.items().get(normalizedIndex);
        long startPositionMs = progressStore.loadProgress(item.key())
                .filter(progress -> !progress.finished())
                .map(PlaybackProgress::positionMs)
                .orElse(0L);

        return new StartPlan(normalizedIndex, item, startPositionMs, reason);
    }

    public PlaybackProgress checkpoint(QueueItem item, long positionMs, long updatedAt) {
        Objects.requireNonNull(item, "item");

        long safePosition = Math.max(0L, Math.min(positionMs, item.durationMs()));
        boolean finished = isFinished(safePosition, item.durationMs());
        return new PlaybackProgress(item.key(), safePosition, item.durationMs(), finished, updatedAt);
    }

    public int nextIndexAfterCompletion(ResolvedQueue queue, int currentIndex) {
        Objects.requireNonNull(queue, "queue");
        int nextIndex = currentIndex + 1;
        return nextIndex < queue.items().size() ? nextIndex : -1;
    }

    private int advancePastFinished(List<QueueItem> items, int startIndex, ProgressStore progressStore) {
        int currentIndex = startIndex;
        while (currentIndex < items.size() - 1) {
            Optional<PlaybackProgress> progress = progressStore.loadProgress(items.get(currentIndex).key());
            if (progress.isEmpty() || !progress.get().finished()) {
                break;
            }
            currentIndex++;
        }
        return currentIndex;
    }

    private boolean isFinished(long positionMs, long durationMs) {
        if (durationMs <= 0L) {
            return false;
        }
        return positionMs >= Math.max(0L, durationMs - FINISHED_THRESHOLD_MS);
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }
}
