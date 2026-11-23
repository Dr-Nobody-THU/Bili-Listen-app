package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import com.bililisten.android.widget.WidgetPlaybackState
import com.bililisten.android.widget.WidgetStateStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResumeRetentionTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val start = 1_700_000_000_000L
    private val month = ResumeRetentionStore.RETENTION_MS

    @Test fun startupAfterThirtyDaysClearsResumeStateIncludingWidgetButKeepsTotalsAndAudio() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback(index = 2)
        val id = repository.favoriteIdOf(playback)
        repository.saveFavorite(playback, start)
        repository.saveProgress(TestFixtures.playback(index = 1), 19_000, start)
        repository.saveProgress(playback, 42_000, start)
        val legacy = ListenStateStore(context)
        legacy.saveProgress(PlaybackProgress(playback.plan.item.key, 50_000, 240_000, false, start))
        repository.addListeningTime(id, 123_000)
        val cached = AudioCacheFiles(File(context.filesDir, "audio_cache")).file(playback.plan.item)
        cached.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(20)) }
        WidgetStateStore(context).save(WidgetPlaybackState(playback, positionMs = 42_000))
        repository.toggleFavorite(playback, start)

        assertTrue(repository.cleanupExpiredResumeRecords(start + month - 1).isEmpty())
        assertEquals(42_000L, repository.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(19_000L, repository.planPlaybackFromQueue(playback.queue, 1).plan.startPositionMs)
        assertEquals(setOf(id), repository.cleanupExpiredResumeRecords(start + month))
        val reopened = ListenRepository(context)
        assertNull(reopened.loadLastPlayback())
        assertNull(WidgetStateStore(context).load())
        assertNull(legacy.loadSession(id))
        assertNull(legacy.loadProgress(playback.plan.item.key))
        assertEquals(0L, reopened.planPlaybackFromQueue(playback.queue, 2).plan.startPositionMs)
        assertEquals(123_000L, reopened.totalListeningTimeMs())
        assertNotNull(reopened.cachedAudioUri(playback.plan.item))
        reopened.toggleFavorite(playback, start + month + 1)
        assertEquals(123_000L, reopened.loadFavorite(id)!!.listeningTimeMs)
    }

    @Test fun playingDuringGracePeriodDoesNotMoveTheUnfavoriteDeadline() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        repository.saveFavorite(playback, start)
        repository.saveProgress(playback, 30_000, start)
        repository.toggleFavorite(playback, start)
        repository.saveProgress(playback, 45_000, start + month - 1)
        // Saving/reading playback never prunes it; only the explicit startup check does.
        assertEquals(45_000L, ListenRepository(context).loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(setOf(repository.favoriteIdOf(playback)), repository.cleanupExpiredResumeRecords(start + month))
        assertNull(repository.loadLastPlayback())
    }

    @Test fun refavoritingCancelsExpirationAndRemovingAgainStartsANewDeadline() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        val id = repository.favoriteIdOf(playback)
        repository.saveFavorite(playback, start)
        repository.saveProgress(playback, 42_000, start)
        repository.toggleFavorite(playback, start)
        repository.toggleFavorite(repository.loadLastPlayback()!!, start + month - 1)
        assertTrue(repository.cleanupExpiredResumeRecords(start + month * 4).isEmpty())
        assertEquals(42_000L, repository.loadFavorite(id)!!.playback.plan.startPositionMs)
        val removedAgain = start + month * 4
        repository.toggleFavorite(playback, removedAgain)
        assertTrue(repository.cleanupExpiredResumeRecords(removedAgain + month - 1).isEmpty())
        assertEquals(setOf(id), repository.cleanupExpiredResumeRecords(removedAgain + month))
    }

    @Test fun upgradingOldUnfavoritedRecordsStartsOneGracePeriodWithoutResettingItEveryLaunch() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        repository.saveProgress(playback, 42_000, start - month * 4)
        // Emulate 0.7.0: resume records exist but no removal timestamps were saved.
        assertTrue(repository.cleanupExpiredResumeRecords(start).isEmpty())
        assertTrue(ListenRepository(context).cleanupExpiredResumeRecords(start + month / 2).isEmpty())
        assertEquals(42_000L, repository.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(setOf(repository.favoriteIdOf(playback)), ListenRepository(context).cleanupExpiredResumeRecords(start + month))
        assertNull(repository.loadLastPlayback())
    }

    @Test fun cleanupKeepsOtherFavoritesTheirWidgetAndSharedLegacyKeys() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback()
        val b = TestFixtures.playback(a.queue.items.map { it.copy(collectionId = "season:other") })
        repository.saveFavorite(a, start)
        repository.saveProgress(a, 42_000, start)
        repository.toggleFavorite(a, start)
        repository.saveFavorite(b, start)
        repository.saveProgress(b, 65_000, start)
        WidgetStateStore(context).save(WidgetPlaybackState(b, positionMs = 65_000))
        ListenStateStore(context).saveProgress(PlaybackProgress(b.plan.item.key, 65_000, 240_000, false, start))
        repository.cleanupExpiredResumeRecords(start + month)
        assertEquals(65_000L, repository.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(65_000L, WidgetStateStore(context).load()!!.positionMs)
        assertEquals(65_000L, repository.planPlaybackFromQueue(b.queue, 0).plan.startPositionMs)
        assertNotNull(ListenStateStore(context).loadProgress(b.plan.item.key))
        assertEquals(0L, repository.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
        assertTrue(repository.cleanupExpiredResumeRecords(start + month * 3).isEmpty())
    }

    @Test fun specialFavoritesRemainProtectedWithoutAnOrdinaryFavoriteEntry() {
        val repository = ListenRepository(context)
        repository.toggleSpecialFavorite(TestFixtures.playback(index = 2), start)
        val local = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        repository.saveProgress(local, 42_000, start)
        assertTrue(repository.cleanupExpiredResumeRecords(start).isEmpty())
        assertTrue(repository.cleanupExpiredResumeRecords(start + month * 4).isEmpty())
        assertEquals(42_000L, repository.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(42_000L, repository.planPlaybackFromQueue(local.queue, 0).plan.startPositionMs)
    }

    @Test fun coldApplicationStartupPrunesBeforeRestoringAnyPlayback() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        val removedAt = System.currentTimeMillis() - month - 1_000
        repository.saveFavorite(playback, removedAt)
        repository.saveProgress(playback, 42_000, removedAt)
        repository.toggleFavorite(playback, removedAt)
        WidgetStateStore(context).save(WidgetPlaybackState(playback))
        assertNotNull(repository.loadLastPlayback())
        val appRepository = AppGraph.repository(context)
        assertNull(appRepository.loadLastPlayback())
        assertNull(WidgetStateStore(context).load())
        assertSame(appRepository, AppGraph.repository(context))
    }
}
