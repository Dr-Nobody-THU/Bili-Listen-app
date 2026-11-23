package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentPlaybackTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun switchingBackResumesAndFourthDistinctItemEvictsLeastRecentlyHeard() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback(index = 0)
        val b = TestFixtures.playback(index = 1)
        val c = TestFixtures.playback(index = 2)
        repository.saveProgress(a, 45_000)
        repository.saveProgress(b, 12_000)
        repository.saveProgress(c, 8_000)
        assertEquals(45_000L, repository.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
        repository.saveProgress(a, 46_000) // Returning to A makes B the oldest.
        repository.saveProgress(TestFixtures.playback(index = 3), 5_000)
        val restored = ListenRepository(context)
        assertEquals(0L, restored.planPlaybackFromQueue(a.queue, 1).plan.startPositionMs)
        assertEquals(8_000L, restored.planPlaybackFromQueue(a.queue, 2).plan.startPositionMs)
        assertEquals(46_000L, restored.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
        assertEquals(0L, restored.planPlaybackFromQueue(a.queue, 4).plan.startPositionMs)
    }

    @Test fun collectionsAreIndependentEvenForTheSameVideoAndFinishedItemsRestart() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback()
        val other = TestFixtures.playback(a.queue.items.map { it.copy(collectionId = "season:456") })
        repository.saveProgress(a, 42_000)
        repository.saveProgress(other, 80_000)
        assertEquals(42_000L, repository.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
        assertEquals(80_000L, repository.planPlaybackFromQueue(other.queue, 0).plan.startPositionMs)
        repository.saveProgress(a, a.plan.item.durationMs)
        assertEquals(0L, repository.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
    }

    @Test fun oldProgressMigratesAtMostThreeEntriesWithoutLosingTheLatestPosition() {
        val playback = TestFixtures.playback()
        val legacy = ListenStateStore(context)
        playback.queue.items.take(4).forEachIndexed { index, item ->
            legacy.saveProgress(PlaybackProgress(item.key, 10_000L + index, item.durationMs, false, index.toLong()))
        }
        val repository = ListenRepository(context)
        assertEquals(10_003L, repository.planPlaybackFromQueue(playback.queue, 3).plan.startPositionMs)
        assertEquals(0L, repository.planPlaybackFromQueue(playback.queue, 0).plan.startPositionMs)
        assertNull(legacy.loadProgress(playback.queue.items[3].key))
    }

    @Test fun cacheKeepsJustLeftItemThenPrunesItOnTheFollowingTransitionEvenOffline() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback()
        repository.saveFavorite(a)
        val files = AudioCacheFiles(File(context.filesDir, "audio_cache"))
        a.queue.items.forEach { files.file(it).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(20)) } }
        val other = TestFixtures.item(8, collection = "season:other")
        files.file(other).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(20)) }
        repository.saveProgress(a, 35_000)
        val b = repository.planPlaybackFromQueue(a.queue, 1)
        repository.saveProgress(b, 4_000)
        repository.maintainAutoCache(b)
        assertNotNull(files.cached(a.plan.item))
        val c = repository.planPlaybackFromQueue(a.queue, 2)
        repository.saveProgress(c, 0)
        repository.maintainAutoCache(c)
        assertNull(files.cached(a.plan.item))
        assertNotNull(files.cached(b.plan.item))
        assertNotNull(files.cached(other))
        assertEquals(35_000L, repository.planPlaybackFromQueue(a.queue, 0).plan.startPositionMs)
    }
}
