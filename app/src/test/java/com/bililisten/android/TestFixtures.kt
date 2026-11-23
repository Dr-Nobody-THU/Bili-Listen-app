package com.bililisten.android

import com.bililisten.android.data.*

object TestFixtures {
    fun item(index: Int, published: Long = (index + 1) * 1000L, collection: String? = "season:123") = QueueItem(
        key = ContentKey("BV18h411v7Lp", index + 1L), title = "第 ${index + 1} 集 · 山野之间的声音",
        durationMs = 240_000L, pageNumber = index + 1, orderInCollection = index,
        collectionId = collection, collectionTitle = collection?.let { "把日常听成风景" },
        coverUrl = null, authorMid = 123L, authorName = "声音收藏家", authorAvatarUrl = null,
        viewCount = 12000L, publishedAtMs = published, description = "一段可以慢慢听的日常。",
    )
    fun playback(items: List<QueueItem> = (0..7).map { item(it) }, index: Int = 0): PlannedPlayback {
        val queue = ResolvedQueue(items.first().collectionId, items.first().collectionTitle, items, index)
        return PlannedPlayback(queue, StartPlan(index, items[index], 23_000L, "test"))
    }
}
