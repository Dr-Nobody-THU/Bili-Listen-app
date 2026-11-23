package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoryPlaybackTest {
    @Test fun removingFavoriteKeepsLastPlaybackRecentProgressSessionAndListeningTime() {
        val context = RuntimeEnvironment.getApplication()
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback(index = 2)
        repository.saveFavorite(playback)
        repository.addListeningTime(repository.favoriteIdOf(playback), 60_000)
        repository.saveProgress(playback, 42_000)
        assertFalse(repository.toggleFavorite(playback))
        val reopened = ListenRepository(context)
        assertEquals(42_000L, reopened.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(42_000L, reopened.planPlaybackFromQueue(playback.queue, 2).plan.startPositionMs)
        assertEquals(2, ListenStateStore(context).loadSession(playback.queue.collectionId!!)!!.currentIndex)
        assertTrue(reopened.toggleFavorite(reopened.loadLastPlayback()!!))
        assertEquals(60_000L, reopened.loadFavorite(repository.favoriteIdOf(playback))!!.listeningTimeMs)
        assertEquals(42_000L, reopened.loadFavorite(repository.favoriteIdOf(playback))!!.playback.plan.startPositionMs)
    }

    @Test fun orderAndPositionSurviveFavoritesReloadAndOfflineRefresh() {
        val context = RuntimeEnvironment.getApplication()
        val repository = ListenRepository(context)
        val original = TestFixtures.playback(index = 2)
        repository.saveFavorite(original)
        val sorted = repository.updateCollectionPlaybackOrder(original, CollectionPlaybackOrder.PUBLISHED_DESC, 42_000L)
        assertEquals(original.plan.item.key, sorted.plan.item.key)
        assertEquals(42_000L, sorted.plan.startPositionMs)
        val restored = ListenRepository(context).loadFavorite("season:123")!!.playback
        assertEquals(sorted, restored)
        assertFalse(repository.isWifiConnected())
        assertEquals(restored, repository.refreshPlaybackSnapshot(restored))
        val reverted = repository.updateCollectionPlaybackOrder(restored, CollectionPlaybackOrder.COLLECTION_DEFAULT)
        assertEquals(original.queue.items, reverted.queue.items)
        assertEquals(42_000L, reverted.plan.startPositionMs)
    }

    @Test fun offlineCacheSnapshotShowsCompletedFilesAndPendingWindow() {
        val context = RuntimeEnvironment.getApplication()
        val playback = TestFixtures.playback(index = 6)
        val files = AudioCacheFiles(java.io.File(context.filesDir, "audio_cache"))
        files.file(playback.queue.items[6]).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(25)) }
        val repository = ListenRepository(context)
        repository.saveFavorite(playback)
        val snapshot = repository.cacheSnapshot(playback)
        assertEquals(1, snapshot.cachedCount)
        assertEquals(25L, snapshot.cachedBytes)
        assertEquals(6, snapshot.targetCount)
        assertEquals(1, snapshot.readyTargetCount)
        assertNotNull(repository.cachedAudioUri(playback.queue.items[6]))
        assertEquals(CachePhase.WAITING_WIFI, snapshot.entries[playback.queue.items[7].key.asStoreKey()]!!.phase)
        assertEquals(CachePhase.NOT_CACHED, snapshot.entries[playback.queue.items[4].key.asStoreKey()]!!.phase)
    }
}
