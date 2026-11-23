package com.bililisten.android.data

/** Original collection positions never change when the displayed queue is sorted. */
object QueueOrdering {
    fun reorder(queue: ResolvedQueue, order: CollectionPlaybackOrder): ResolvedQueue {
        val originalOrder = queue.items.sortedBy { it.orderInCollection }
        val sorted = when (order) {
            CollectionPlaybackOrder.COLLECTION_DEFAULT, CollectionPlaybackOrder.FAVORITED_ASC -> originalOrder
            CollectionPlaybackOrder.FAVORITED_DESC -> originalOrder.reversed()
            CollectionPlaybackOrder.MANUAL -> queue.items
            CollectionPlaybackOrder.PUBLISHED_ASC -> originalOrder.sortedWith(
                compareBy<QueueItem> { it.publishedAtMs.takeIf { time -> time > 0 } ?: Long.MAX_VALUE }
                    .thenBy { it.orderInCollection },
            )
            CollectionPlaybackOrder.PUBLISHED_DESC -> originalOrder.sortedWith(
                compareByDescending<QueueItem> { it.publishedAtMs.takeIf { time -> time > 0 } ?: Long.MIN_VALUE }
                    .thenByDescending { it.orderInCollection },
            )
        }
        val requestedKey = queue.items[queue.requestedIndex].key
        return queue.copy(items = sorted, requestedIndex = sorted.indexOfFirst { it.key == requestedKey })
    }

    fun cacheWindow(playback: PlannedPlayback, size: Int = 6): List<QueueItem> {
        val items = playback.queue.items
        return (0 until minOf(size, items.size)).map { offset ->
            items[(playback.plan.startIndex + offset) % items.size]
        }
    }
}
