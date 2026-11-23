@file:Suppress("DEPRECATION")

package com.bililisten.android.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheFavoritePolicyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val files get() = AudioCacheFiles(File(context.filesDir, "audio_cache"))
    private val start = 1_700_000_000_000L
    private val month = CacheRetentionStore.RETENTION_MS

    @Test fun unfavoritedQueueNeverRequestsOrPrunesAudioEvenOnWifi() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val connectivity = shadowOf(manager)
        connectivity.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true))
        val wifi = manager.activeNetwork!!
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        connectivity.setNetworkCapabilities(wifi, caps)
        val repository = ListenRepository(context, BilibiliApi { _, _ -> error("Unfavorited content must not request audio") })
        val playback = TestFixtures.playback(index = 4)
        val old = cache(playback.queue.items.first())
        cache(playback.plan.item)
        assertTrue(repository.isWifiConnected())
        val maintenance = repository.maintainAutoCache(playback)
        assertEquals(0, maintenance.targetCount)
        assertEquals(0, maintenance.deletedCount)
        assertTrue(old.isFile)
        val snapshot = repository.cacheSnapshot(playback)
        assertFalse(snapshot.autoCacheEnabled)
        assertEquals(0, snapshot.targetCount)
        assertEquals(2, snapshot.cachedCount)
        assertEquals(CachePhase.NOT_CACHED, snapshot.entries[playback.queue.items[5].key.asStoreKey()]!!.phase)
        assertNotNull(repository.cachedAudioUri(playback.plan.item))
    }

    @Test fun removingFavoriteStopsPrefetchAndExpiresOnlyOnStartupAfterThirtyDays() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        cache(playback.plan.item)
        repository.saveFavorite(playback, start)
        repository.saveProgress(playback, 42_000, start)
        repository.addListeningTime(repository.favoriteIdOf(playback), 123_000)
        assertEquals(6, repository.cacheSnapshot(playback).targetCount)
        repository.toggleFavorite(playback, start)
        assertFalse(repository.cacheSnapshot(playback).autoCacheEnabled)
        assertEquals(0, repository.maintainAutoCache(playback).targetCount)
        repository.saveProgress(playback, 65_000, start + month - 1)
        assertEquals(0, repository.cleanupExpiredAudioCache(start + month - 1))
        // File accesses and progress checkpoints do not renew the removal date.
        assertNotNull(repository.cachedAudioUri(playback.plan.item))
        val reopened = ListenRepository(context)
        assertEquals(1, reopened.cleanupExpiredAudioCache(start + month))
        assertNull(reopened.cachedAudioUri(playback.plan.item))
        assertEquals(65_000L, reopened.loadLastPlayback()!!.plan.startPositionMs)
        assertEquals(123_000L, reopened.totalListeningTimeMs())
        assertEquals(0, reopened.cleanupExpiredAudioCache(start + month * 2))
    }

    @Test fun refavoritingProtectsCacheAndALaterRemovalRestartsTheDeadline() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        cache(playback.plan.item)
        repository.saveFavorite(playback, start)
        repository.toggleFavorite(playback, start)
        repository.toggleFavorite(playback, start + month - 1)
        assertEquals(0, repository.cleanupExpiredAudioCache(start + month * 4))
        assertNotNull(repository.cachedAudioUri(playback.plan.item))
        val removedAgain = start + month * 4
        repository.toggleFavorite(playback, removedAgain)
        assertEquals(0, repository.cleanupExpiredAudioCache(removedAgain + month - 1))
        assertEquals(1, repository.cleanupExpiredAudioCache(removedAgain + month))
    }

    @Test fun specialFavoriteProtectsItsFileUntilItsLastOwnerIsRemoved() {
        val repository = ListenRepository(context)
        val playback = TestFixtures.playback()
        val other = playback.queue.items[1]
        cache(playback.plan.item)
        cache(other)
        repository.saveFavorite(playback, start)
        repository.toggleSpecialFavorite(playback, start)
        repository.toggleFavorite(playback, start)
        assertEquals(1, repository.cleanupExpiredAudioCache(start + month))
        assertNull(repository.cachedAudioUri(other))
        assertNotNull(repository.cachedAudioUri(playback.plan.item))
        val special = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        assertTrue(repository.cacheSnapshot(special).autoCacheEnabled)
        assertEquals(1, repository.cacheSnapshot(special).targetCount)
        repository.toggleSpecialFavorite(playback, start + month)
        assertEquals(0, repository.maintainAutoCache(special).targetCount)
        assertEquals(0, repository.cleanupExpiredAudioCache(start + month * 2 - 1))
        assertEquals(1, repository.cleanupExpiredAudioCache(start + month * 2))
    }

    @Test fun singleFavoriteDoesNotProtectUnrelatedFilesInTheSharedSingleDirectory() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback(listOf(TestFixtures.item(0, collection = null)))
        val b = TestFixtures.playback(listOf(TestFixtures.item(1, collection = null)))
        cache(a.plan.item)
        cache(b.plan.item)
        repository.saveFavorite(a, start)
        assertEquals(1, repository.cleanupExpiredAudioCache(start + month))
        assertNotNull(repository.cachedAudioUri(a.plan.item))
        assertNull(repository.cachedAudioUri(b.plan.item))
    }

    @Test fun legacyFilesGetOneGracePeriodAndClockRollbackDoesNotExpireThem() {
        val repository = ListenRepository(context)
        val item = TestFixtures.item(0)
        cache(item).setLastModified(start - month * 4)
        assertEquals(0, repository.cleanupExpiredAudioCache(start))
        assertEquals(0, ListenRepository(context).cleanupExpiredAudioCache(start - month))
        assertEquals(0, ListenRepository(context).cleanupExpiredAudioCache(start + month - 1))
        assertEquals(1, ListenRepository(context).cleanupExpiredAudioCache(start + month))
    }

    private fun cache(item: QueueItem): File = files.file(item).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(20))
    }
}
