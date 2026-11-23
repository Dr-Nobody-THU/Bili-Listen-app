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
class SpecialFavoritesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val first = "BV18h411v7Lp"
    private val second = "BV1xx411c7mD"
    private val third = "BV1yy411c7mD"
    private fun article(bvid: String, parts: Int = 1, collection: String = "season:$bvid") =
        (0 until parts).map { index -> TestFixtures.item(index, collection = collection)
            .copy(key = ContentKey(bvid, index + 1L)) }
    private fun saved(repository: ListenRepository) = repository.loadFavorite(SpecialFavorites.ID)!!.playback

    @Test fun heartAddsOnlyTheCurrentPartAndPersistsTheExactInsertionOrder() {
        val repository = ListenRepository(context)
        val source = TestFixtures.playback(article(first, 3) + article(second), index = 2)
        assertTrue(repository.toggleSpecialFavorite(source))
        assertEquals(listOf(source.plan.item.key), saved(repository).queue.items.map { it.key })
        assertFalse(repository.isSpecialFavorited(source.queue.items[0].key))
        assertTrue(repository.isSpecialFavorited(source.queue.items[2].key))
        assertTrue(repository.toggleSpecialFavorite(TestFixtures.playback(article(second))))
        assertTrue(repository.toggleSpecialFavorite(TestFixtures.playback(article(first, 3), index = 0)))
        val expected = listOf(source.queue.items[2].key, ContentKey(second, 1), source.queue.items[0].key)
        assertEquals(expected, saved(ListenRepository(context)).queue.items.map { it.key })
        // The same BV+CID in another source collection is the same heart; other P parts stay selected.
        val duplicate = TestFixtures.playback(article(first, 3, collection = "season:other"), index = 2)
        assertTrue(repository.isSpecialFavorited(duplicate.plan.item.key))
        assertFalse(repository.toggleSpecialFavorite(duplicate))
        assertEquals(expected.drop(1), saved(repository).queue.items.map { it.key })
        repository.toggleSpecialFavorite(source)
        assertEquals(expected.drop(1) + expected.first(), saved(repository).queue.items.map { it.key })
        val next = repository.adjacentPlayback(saved(repository), 1)!!
        assertEquals(ContentKey(first, 1), next.plan.item.key)
        assertEquals(ContentKey(first, 3), repository.adjacentPlayback(next, 1)!!.plan.item.key)
    }

    @Test fun refreshAndResumeKeepTheLocalQueueAndIndependentCollectionFavorites() {
        val repository = ListenRepository(context)
        val source = TestFixtures.playback(article(first) + article(second))
        repository.saveFavorite(source)
        repository.toggleSpecialFavorite(source)
        repository.toggleSpecialFavorite(TestFixtures.playback(article(second)))
        val local = saved(repository)
        repository.saveProgress(local, 41_000)
        repository.toggleFavorite(source)
        val reopened = ListenRepository(context)
        val restored = reopened.refreshPlaybackSnapshot(reopened.loadLastPlayback()!!)
        assertEquals(SpecialFavorites.ID, restored.queue.collectionId)
        assertEquals(listOf(first, second), restored.queue.items.map { it.key.bvid })
        assertEquals(41_000L, restored.plan.startPositionMs)
        assertEquals(listOf(SpecialFavorites.ID), reopened.listFavorites().map { it.id })
        repository.toggleFavorite(local) // The regular collection button cannot erase all hearts.
        assertEquals(2, saved(repository).queue.items.size)
    }

    @Test fun skipsCrossCollectionsUseOriginalCachesAndRememberRecentPositions() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback(article(first))
        val b = TestFixtures.playback(article(second))
        repository.toggleSpecialFavorite(a)
        repository.toggleSpecialFavorite(b)
        val files = AudioCacheFiles(File(context.filesDir, "audio_cache"))
        files.file(a.plan.item).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(32)) }
        files.file(b.plan.item).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(42)) }
        val local = saved(repository)
        assertEquals(repository.cachedAudioUri(a.plan.item), repository.cachedAudioUri(local.plan.item))
        repository.saveProgress(local, 42_000)
        val next = repository.adjacentPlayback(local, 1)!!
        assertEquals(SpecialFavorites.ID, next.queue.collectionId)
        assertEquals(second, next.plan.item.key.bvid)
        assertEquals(repository.cachedAudioUri(b.plan.item), repository.cachedAudioUri(next.plan.item))
        repository.saveProgress(next, 8_000)
        assertEquals(42_000L, repository.adjacentPlayback(next, -1)!!.plan.startPositionMs)
        assertEquals(first, repository.adjacentPlayback(next, 1)!!.plan.item.key.bvid)
    }

    @Test fun removingCurrentPartLetsItFinishAndLeavesOtherSelectedPartsPlayable() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback(article(first, 2))
        repository.toggleSpecialFavorite(a)
        repository.toggleSpecialFavorite(TestFixtures.playback(article(first, 2), index = 1))
        repository.toggleSpecialFavorite(TestFixtures.playback(article(second)))
        repository.toggleSpecialFavorite(TestFixtures.playback(article(third)))
        val active = saved(repository)
        assertFalse(repository.toggleSpecialFavorite(active))
        val current = repository.normalizePlaybackOrder(active)
        assertEquals(first, current.plan.item.key.bvid)
        assertEquals(2, current.queue.items.count { it.key.bvid == first })
        assertEquals(ContentKey(first, 2), repository.adjacentPlayback(current, 1)!!.plan.item.key)
        assertEquals(third, repository.adjacentPlayback(current, -1)!!.plan.item.key.bvid)
        repository.saveProgress(active, 23_000) // An old service snapshot cannot restore membership.
        assertEquals(listOf(ContentKey(first, 2), ContentKey(second, 1), ContentKey(third, 1)), saved(repository).queue.items.map { it.key })
        assertEquals(ContentKey(first, 2), repository.loadLastPlayback()!!.plan.item.key)
        val next = repository.adjacentPlayback(current, 1)!!
        assertEquals(third, repository.adjacentPlayback(next, -1)!!.plan.item.key.bvid)
    }

    @Test fun removingLastHeartLeavesNoReplayableSavedPlaylist() {
        val repository = ListenRepository(context)
        repository.toggleSpecialFavorite(TestFixtures.playback(article(first)))
        val active = saved(repository)
        repository.saveProgress(active, 40_000)
        repository.toggleSpecialFavorite(active)
        repository.saveProgress(active, 41_000)
        assertNull(repository.loadFavorite(SpecialFavorites.ID))
        assertNull(repository.loadLastPlayback())
        assertNull(repository.adjacentPlayback(active, 1))
        assertEquals(first, repository.normalizePlaybackOrder(active).plan.item.key.bvid)
    }

    @Test fun oldWholeArticleFavoritesRetainTheirPartsAndCursorAndCanBeRemovedIndividually() {
        val old = TestFixtures.playback(article(first, 3), index = 2).let {
            it.copy(queue = it.queue.copy(collectionId = SpecialFavorites.ID, collectionTitle = SpecialFavorites.TITLE))
        }
        context.getSharedPreferences("special_favorites", 0).edit()
            .putString("items", PlaybackJsonCodec.encode(old))
            .putStringSet("verified", setOf(first))
            .putString("cursor", old.plan.item.key.asStoreKey()).putLong("position", 42_000).commit()
        val repository = ListenRepository(context)
        assertEquals(3, saved(repository).queue.items.size)
        assertEquals(42_000L, saved(repository).plan.startPositionMs)
        old.queue.items.forEach { assertTrue(repository.isSpecialFavorited(it.key)) }
        repository.toggleSpecialFavorite(TestFixtures.playback(article(first, 3), index = 1))
        assertEquals(listOf(1, 3), saved(repository).queue.items.map { it.pageNumber })
        assertEquals(42_000L, saved(repository).plan.startPositionMs)
        repository.saveProgress(old, 43_000) // A stale snapshot must not restore the removed P2.
        val restored = saved(ListenRepository(context))
        assertEquals(listOf(1, 3), restored.queue.items.map { it.pageNumber })
        assertEquals(3, restored.plan.item.pageNumber)
        assertEquals(43_000L, restored.plan.startPositionMs)
    }

    @Test fun favoriteTimeOrdersFollowAdditionSequenceIncludingInterleavedPartsAndRefavorites() {
        val repository = ListenRepository(context)
        val a = article(first, 3).map { it.copy(publishedAtMs = 10_000) }
        val b = article(second, 2).map { it.copy(publishedAtMs = 20_000) }
        val additions = listOf(TestFixtures.playback(a, 2), TestFixtures.playback(b, 1), TestFixtures.playback(a, 0))
        additions.forEach { repository.toggleSpecialFavorite(it, 1_000) } // Equal timestamps retain tap order.
        val keys = additions.map { it.plan.item.key }
        assertEquals(CollectionPlaybackOrder.FAVORITED_ASC, repository.loadCollectionPlaybackOrder(SpecialFavorites.ID))
        val descending = repository.updateCollectionPlaybackOrder(saved(repository), CollectionPlaybackOrder.FAVORITED_DESC, 42_000)
        assertEquals(keys.reversed(), descending.queue.items.map { it.key })
        assertEquals(keys.first(), descending.plan.item.key)
        assertEquals(42_000L, descending.plan.startPositionMs)
        assertEquals(keys.reversed(), saved(ListenRepository(context)).queue.items.map { it.key })
        val ascending = repository.updateCollectionPlaybackOrder(descending, CollectionPlaybackOrder.FAVORITED_ASC)
        assertEquals(keys, ascending.queue.items.map { it.key })
        repository.toggleSpecialFavorite(additions[1])
        repository.toggleSpecialFavorite(additions[1])
        assertEquals(listOf(keys[0], keys[2], keys[1]), saved(repository).queue.items.map { it.key })
    }

    @Test fun manualOrderSurvivesRestartAndTimeSortingWithoutMovingTheCurrentPartOrItsPosition() {
        val repository = ListenRepository(context)
        val source = article(first, 4)
        source.indices.forEach { repository.toggleSpecialFavorite(TestFixtures.playback(source, it)) }
        val oldSnapshot = saved(repository)
        repository.saveProgress(oldSnapshot, 42_000)
        val manual = listOf(source[2], source[0], source[3], source[1]).map { it.key }
        repository.saveSpecialFavoriteOrder(manual)
        val restored = saved(ListenRepository(context))
        assertEquals(CollectionPlaybackOrder.MANUAL, repository.loadCollectionPlaybackOrder(SpecialFavorites.ID))
        assertEquals(manual, restored.queue.items.map { it.key })
        assertEquals(source[0].key, restored.plan.item.key)
        assertEquals(1, restored.plan.startIndex)
        assertEquals(42_000L, restored.plan.startPositionMs)
        assertEquals(source[3].key, repository.adjacentPlayback(oldSnapshot, 1)!!.plan.item.key)
        assertEquals(source[2].key, repository.adjacentPlayback(oldSnapshot, -1)!!.plan.item.key)
        assertEquals(listOf(source[0], source[3], source[1], source[2]).map { it.key }, QueueOrdering.cacheWindow(restored).map { it.key })
        val descending = repository.updateCollectionPlaybackOrder(restored, CollectionPlaybackOrder.FAVORITED_DESC)
        assertEquals(source.reversed().map { it.key }, descending.queue.items.map { it.key })
        assertEquals(manual, repository.specialFavoriteManualItems().map { it.key })
        val ascending = repository.updateCollectionPlaybackOrder(descending, CollectionPlaybackOrder.FAVORITED_ASC)
        assertEquals(source.map { it.key }, ascending.queue.items.map { it.key })
        val reordered = repository.updateCollectionPlaybackOrder(ascending, CollectionPlaybackOrder.MANUAL)
        assertEquals(manual, reordered.queue.items.map { it.key })
        repository.saveProgress(oldSnapshot, 43_000) // Old queue snapshots cannot overwrite a saved drag order.
        assertEquals(manual, saved(repository).queue.items.map { it.key })
        assertEquals(43_000L, saved(repository).plan.startPositionMs)
    }

    @Test fun staleManualDraftMergesLiveHeartsAndNewEntriesAppendWithoutResurrectingRemovedParts() {
        val repository = ListenRepository(context)
        val source = article(first, 5)
        (0..2).forEach { repository.toggleSpecialFavorite(TestFixtures.playback(source, it)) }
        repository.updateCollectionPlaybackOrder(saved(repository), CollectionPlaybackOrder.FAVORITED_DESC)
        assertEquals(source.take(3).reversed().map { it.key }, repository.specialFavoriteManualItems().map { it.key })
        val draft = repository.specialFavoriteManualItems().map { it.key }
        repository.toggleSpecialFavorite(TestFixtures.playback(source, 1))
        repository.toggleSpecialFavorite(TestFixtures.playback(source, 3))
        repository.saveSpecialFavoriteOrder(draft + draft) // Defensive deduplication.
        assertEquals(listOf(2, 0, 3).map { source[it].key }, saved(repository).queue.items.map { it.key })
        repository.toggleSpecialFavorite(TestFixtures.playback(source, 4))
        repository.toggleSpecialFavorite(TestFixtures.playback(source, 2))
        repository.toggleSpecialFavorite(TestFixtures.playback(source, 2))
        assertEquals(listOf(0, 3, 4, 2).map { source[it].key }, saved(ListenRepository(context)).queue.items.map { it.key })
        assertEquals(4, saved(repository).queue.items.size)
    }

    @Test fun legacyPublicationOrderBecomesFavoriteTimeOrderAndLeavesNormalCollectionsAlone() {
        val repository = ListenRepository(context)
        val source = TestFixtures.playback(article(first, 2))
        source.queue.items.indices.forEach { repository.toggleSpecialFavorite(TestFixtures.playback(source.queue.items, it)) }
        context.getSharedPreferences("app_state", 0).edit()
            .putString("collection_order:${SpecialFavorites.ID}", "PUBLISHED_DESC")
            .putString("collection_order:${source.queue.collectionId}", "PUBLISHED_ASC").commit()
        assertEquals(CollectionPlaybackOrder.FAVORITED_DESC, repository.loadCollectionPlaybackOrder(SpecialFavorites.ID))
        assertEquals(source.queue.items.reversed().map { it.key }, saved(repository).queue.items.map { it.key })
        assertEquals(CollectionPlaybackOrder.PUBLISHED_ASC, repository.loadCollectionPlaybackOrder(source.queue.collectionId))
    }

    @Test fun specialCacheMaintenancePreservesOtherItemsFromTheSourceCollection() {
        val repository = ListenRepository(context)
        val a = TestFixtures.playback(article(first))
        repository.toggleSpecialFavorite(a)
        val files = AudioCacheFiles(File(context.filesDir, "audio_cache"))
        val other = article(second, collection = a.queue.collectionId!!).single()
        files.file(other).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(20)) }
        repository.maintainAutoCache(saved(repository))
        assertNotNull(files.cached(other))
    }

    @Test fun specialMembershipChangesNeverChangeGlobalListeningTimeOrCountItTwice() {
        val repository = ListenRepository(context)
        val source = TestFixtures.playback(article(first))
        repository.addListeningTime(repository.favoriteIdOf(source), 60_000)
        repository.toggleSpecialFavorite(source)
        repository.addListeningTime(SpecialFavorites.ID, 30_000)
        assertEquals(90_000L, repository.totalListeningTimeMs())
        repository.toggleSpecialFavorite(source)
        assertEquals(90_000L, ListenRepository(context).totalListeningTimeMs())
        repository.toggleSpecialFavorite(source)
        assertEquals(30_000L, repository.loadFavorite(SpecialFavorites.ID)!!.listeningTimeMs)
        assertEquals(90_000L, repository.totalListeningTimeMs())
    }
}
