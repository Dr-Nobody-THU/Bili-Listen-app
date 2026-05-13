package com.bililisten.prototype.core;

import com.bililisten.prototype.model.CollectionSession;
import com.bililisten.prototype.model.PlaybackProgress;
import com.bililisten.prototype.model.QueueItem;
import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.ResumePolicy;
import com.bililisten.prototype.model.StartPlan;
import com.bililisten.prototype.model.VideoReference;
import com.bililisten.prototype.store.CollectionSessionStore;
import com.bililisten.prototype.store.ProgressStore;

import java.util.Optional;

public final class ListeningSessionService {
    public PlannedPlayback plan(
            QueueResolver resolver,
            VideoReference reference,
            ResumePolicy resumePolicy,
            ProgressStore progressStore,
            CollectionSessionStore sessionStore
    ) {
        ResolvedQueue queue = resolver.resolve(reference);
        Optional<CollectionSession> savedSession = queue.collectionId() == null
                ? Optional.empty()
                : sessionStore.loadSession(queue.collectionId());

        StartPlan startPlan = new PlaybackPlanner().planStart(queue, savedSession, progressStore, resumePolicy);
        return new PlannedPlayback(queue, startPlan);
    }

    public PlaybackProgress saveCurrentProgress(
            PlannedPlayback playback,
            long positionMs,
            long updatedAt,
            ProgressStore progressStore,
            CollectionSessionStore sessionStore
    ) {
        QueueItem item = playback.plan().item();
        PlaybackProgress progress = new PlaybackPlanner().checkpoint(item, positionMs, updatedAt);
        progressStore.saveProgress(progress);
        saveSession(playback, playback.plan().startIndex(), updatedAt, sessionStore);
        return progress;
    }

    public AdvanceOutcome advance(
            PlannedPlayback playback,
            long updatedAt,
            ProgressStore progressStore,
            CollectionSessionStore sessionStore
    ) {
        QueueItem completedItem = playback.plan().item();
        PlaybackPlanner planner = new PlaybackPlanner();
        PlaybackProgress finishedProgress = planner.checkpoint(completedItem, completedItem.durationMs(), updatedAt);
        progressStore.saveProgress(finishedProgress);

        int nextIndex = planner.nextIndexAfterCompletion(playback.queue(), playback.plan().startIndex());
        if (nextIndex < 0) {
            saveSession(playback, playback.plan().startIndex(), updatedAt, sessionStore);
            return new AdvanceOutcome(completedItem, null, true);
        }

        saveSession(playback, nextIndex, updatedAt, sessionStore);
        Optional<CollectionSession> nextSession = playback.queue().collectionId() == null
                ? Optional.empty()
                : Optional.of(new CollectionSession(playback.queue().collectionId(), nextIndex, updatedAt));
        StartPlan nextPlan = planner.planStart(
                playback.queue(),
                nextSession,
                progressStore,
                ResumePolicy.PREFER_SAVED_COLLECTION_POSITION
        );
        return new AdvanceOutcome(completedItem, new PlannedPlayback(playback.queue(), nextPlan), false);
    }

    private void saveSession(
            PlannedPlayback playback,
            int currentIndex,
            long updatedAt,
            CollectionSessionStore sessionStore
    ) {
        if (playback.queue().collectionId() == null) {
            return;
        }
        sessionStore.saveSession(new CollectionSession(playback.queue().collectionId(), currentIndex, updatedAt));
    }

    public record PlannedPlayback(ResolvedQueue queue, StartPlan plan) {
    }

    public record AdvanceOutcome(QueueItem completedItem, PlannedPlayback nextPlayback, boolean reachedEnd) {
    }
}
