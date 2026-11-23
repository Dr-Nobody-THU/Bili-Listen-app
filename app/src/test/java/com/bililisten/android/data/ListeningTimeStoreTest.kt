package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningTimeStoreTest {
    @Test fun totalsSurviveRepositoryRecreationProgressClearingAndRefavoriting() {
        val context = RuntimeEnvironment.getApplication()
        val repository = ListenRepository(context)
        val a = TestFixtures.playback()
        val b = TestFixtures.playback((0..2).map { TestFixtures.item(it, collection = "season:456") })
        repository.saveFavorite(a)
        repository.saveFavorite(b)
        val aId = repository.favoriteIdOf(a)
        val bId = repository.favoriteIdOf(b)
        // Legacy favorites have no total; never fabricate totals from saved positions.
        repository.saveProgress(a, 100_000)
        assertEquals(0L, repository.loadFavorite(aId)!!.listeningTimeMs)
        repository.addListeningTime(aId, 65_000)
        repository.addListeningTime(bId, 3_000)
        repository.addListeningTime(aId, 2_000)
        repository.saveProgress(a, 2_000)
        repository.clearCollectionProgress(a.queue)
        repository.toggleFavorite(a)
        repository.toggleFavorite(a)
        val reopened = ListenRepository(context)
        assertEquals(67_000L, reopened.loadFavorite(aId)!!.listeningTimeMs)
        assertEquals(3_000L, reopened.listFavorites().single { it.id == bId }.listeningTimeMs)
        assertEquals("1分钟", formatListeningTime(67_000))
        assertEquals("25小时1分钟", formatListeningTime(90_060_000))
    }

    @Test fun sharedTotalIncludesExistingUnfavoritedAndSingleVideoRecordsWithoutCountingProgress() {
        val context = RuntimeEnvironment.getApplication()
        val repository = ListenRepository(context)
        val a = TestFixtures.playback()
        val b = TestFixtures.playback((0..2).map { TestFixtures.item(it, collection = "season:456") })
        repository.saveFavorite(a)
        repository.saveFavorite(b)
        // Simulate the original per-collection data already present before this upgrade.
        context.getSharedPreferences("collection_listening_time", 0).edit()
            .putLong(repository.favoriteIdOf(a), 60L * 60 * 60 * 1000)
            .putLong(repository.favoriteIdOf(b), 12L * 60 * 60 * 1000)
            .putLong("single:older-video", 3L * 60 * 60 * 1000).commit()
        assertEquals(75L * 60 * 60 * 1000, repository.totalListeningTimeMs())
        repository.toggleFavorite(b)
        repository.clearCollectionProgress(a.queue)
        repository.saveProgress(a, 8_000_000)
        val reopened = ListenRepository(context)
        assertEquals(75L * 60 * 60 * 1000, reopened.totalListeningTimeMs())
        reopened.addListeningTime(repository.favoriteIdOf(b), 1_500)
        assertEquals(75L * 60 * 60 * 1000 + 1_500, repository.totalListeningTimeMs())
        assertEquals(1, reopened.listFavorites().size)
    }

    @Test fun summingLargeTotalsSaturatesWithoutTurningNegative() {
        val store = ListeningTimeStore(RuntimeEnvironment.getApplication())
        store.add("season:a", Long.MAX_VALUE - 10)
        store.add("season:b", 100)
        assertEquals(Long.MAX_VALUE, store.totalMs())
        assertEquals(Long.MAX_VALUE - 10, store.totalMs("season:a"))
    }
}
