package com.bililisten.android.data

import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Test

class QueueOrderingTest {
    @Test fun switchingAllOrdersRestoresOriginalAndKeepsCurrentItem() {
        val queue = TestFixtures.playback(listOf(
            TestFixtures.item(0, 3000), TestFixtures.item(1, 1000), TestFixtures.item(2, 2000),
        ), 1).queue
        val ascending = QueueOrdering.reorder(queue, CollectionPlaybackOrder.PUBLISHED_ASC)
        val descending = QueueOrdering.reorder(ascending, CollectionPlaybackOrder.PUBLISHED_DESC)
        val original = QueueOrdering.reorder(descending, CollectionPlaybackOrder.COLLECTION_DEFAULT)
        assertEquals(listOf(1, 2, 0), ascending.items.map { it.orderInCollection })
        assertEquals(listOf(0, 2, 1), descending.items.map { it.orderInCollection })
        assertEquals(queue, original)
        for (sorted in listOf(ascending, descending, original)) assertEquals(queue.items[1].key, sorted.items[sorted.requestedIndex].key)
    }

    @Test fun multipartVideoCanBeReversedWithIdenticalPublicationDates() {
        val queue = TestFixtures.playback((0..3).map { TestFixtures.item(it, 1000, "video:BV18h411v7Lp") }).queue
        assertEquals(listOf(3, 2, 1, 0), QueueOrdering.reorder(queue, CollectionPlaybackOrder.PUBLISHED_DESC).items.map { it.orderInCollection })
    }

    @Test fun missingDatesStayAtEndInBothDirections() {
        val queue = TestFixtures.playback(listOf(TestFixtures.item(0, 0), TestFixtures.item(1, 3000), TestFixtures.item(2, 1000))).queue
        assertEquals(listOf(2, 1, 0), QueueOrdering.reorder(queue, CollectionPlaybackOrder.PUBLISHED_ASC).items.map { it.orderInCollection })
        assertEquals(listOf(1, 2, 0), QueueOrdering.reorder(queue, CollectionPlaybackOrder.PUBLISHED_DESC).items.map { it.orderInCollection })
    }

    @Test fun prefetchUsesCurrentOrderAndWrapsWithoutDuplicates() {
        val playback = TestFixtures.playback(index = 6)
        assertEquals(listOf(6, 7, 0, 1, 2, 3), QueueOrdering.cacheWindow(playback).map { it.orderInCollection })
        val short = TestFixtures.playback((0..2).map { TestFixtures.item(it) }, 2)
        assertEquals(listOf(2, 0, 1), QueueOrdering.cacheWindow(short).map { it.orderInCollection })
    }
}
