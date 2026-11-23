package com.bililisten.android.data

import android.content.Context
import org.json.JSONArray

object SpecialFavorites {
    const val ID = "local:special-favorites"
    const val TITLE = "特别收藏"
}

/** Membership is independent of saved playback snapshots and of ordinary collection favorites. */
internal class SpecialFavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("special_favorites", Context.MODE_PRIVATE)

    fun contains(key: ContentKey): Boolean = items().any { it.key == key }

    fun toggle(playback: PlannedPlayback, now: Long): Boolean = synchronized(lock) {
        val item = playback.plan.item
        val previous = items()
        val adding = previous.none { it.key == item.key }
        val updated = if (adding) previous + item else previous.filterNot { it.key == item.key }
        writeItems(updated, now)
        adding
    }

    fun favorite(): FavoriteCollection? {
        val items = items()
        if (items.isEmpty()) return null
        val cursor = preferences.getString("cursor", null)
        val index = items.indexOfFirst { it.key.asStoreKey() == cursor }.coerceAtLeast(0)
        val position = if (items[index].key.asStoreKey() == cursor) preferences.getLong("position", 0).coerceAtLeast(0) else 0L
        val queue = ResolvedQueue(SpecialFavorites.ID, SpecialFavorites.TITLE, items, index)
        return FavoriteCollection(SpecialFavorites.ID,
            PlannedPlayback(queue, StartPlan(index, items[index], position, "special favorites")),
            preferences.getLong("updated_at", 0))
    }

    fun savePosition(playback: PlannedPlayback) = synchronized(lock) {
        if (items().none { it.key == playback.plan.item.key }) return@synchronized
        preferences.edit().putString("cursor", playback.plan.item.key.asStoreKey())
            .putLong("position", playback.plan.startPositionMs.coerceAtLeast(0)).apply()
    }

    // The stored membership sequence is the favorite-time sequence, including legacy favorites.
    // Manual sorting has its own key list, so it can never overwrite that history.
    fun manualItems(fallbackOrder: CollectionPlaybackOrder): List<QueueItem> = synchronized(lock) {
        val items = items()
        val keys = manualKeys()
        if (keys == null) {
            if (fallbackOrder == CollectionPlaybackOrder.FAVORITED_DESC) items.reversed() else items
        } else orderByKeys(items, keys)
    }

    fun saveManualOrder(keys: List<ContentKey>) = synchronized(lock) {
        // Merge with live membership: a draft cannot resurrect removed parts or lose new hearts.
        val latest = manualItems(CollectionPlaybackOrder.FAVORITED_ASC)
        preferences.edit().putString("manual_order", JSONArray(orderByKeys(latest, keys.map { it.asStoreKey() })
            .map { it.key.asStoreKey() }).toString()).apply()
    }

    /** Keep a removed, currently sounding part until the next transition, without saving it back. */
    fun orderedQueue(queue: ResolvedQueue, order: CollectionPlaybackOrder): ResolvedQueue = synchronized(lock) {
        val latest = when (order) {
            CollectionPlaybackOrder.MANUAL -> manualItems(CollectionPlaybackOrder.FAVORITED_ASC)
            CollectionPlaybackOrder.FAVORITED_DESC -> items().reversed()
            else -> items()
        }.toMutableList()
        val current = queue.items[queue.requestedIndex]
        if (latest.none { it.key == current.key }) {
            val successor = queue.items.drop(queue.requestedIndex + 1)
                .firstOrNull { old -> latest.any { it.key == old.key } }
            val insertion = successor?.let { next -> latest.indexOfFirst { it.key == next.key } } ?: latest.size
            latest.add(insertion, current)
        }
        ResolvedQueue(SpecialFavorites.ID, SpecialFavorites.TITLE, latest,
            latest.indexOfFirst { it.key == current.key })
    }

    private fun manualKeys(): List<String>? = preferences.getString("manual_order", null)?.let { raw ->
        runCatching { JSONArray(raw).let { array -> List(array.length()) { array.getString(it) } } }.getOrNull()
    }

    private fun orderByKeys(items: List<QueueItem>, keys: List<String>): List<QueueItem> {
        val remaining = items.associateByTo(linkedMapOf()) { it.key.asStoreKey() }
        return keys.mapNotNull { remaining.remove(it) } + remaining.values
    }

    private fun items(): List<QueueItem> = preferences.getString("items", null)?.let { raw ->
        runCatching { PlaybackJsonCodec.decode(raw).queue.items }.getOrNull()
    }.orEmpty()

    private fun writeItems(items: List<QueueItem>, now: Long) {
        val edit = preferences.edit().putLong("updated_at", now).remove("verified")
        if (items.isEmpty()) {
            edit.remove("items").remove("cursor").remove("position").remove("manual_order")
        } else {
            val ordered = items.mapIndexed { index, item -> item.copy(orderInCollection = index) }
            val queue = ResolvedQueue(SpecialFavorites.ID, SpecialFavorites.TITLE, ordered, 0)
            edit.putString("items", PlaybackJsonCodec.encode(PlannedPlayback(queue, StartPlan(0, ordered[0], 0, "membership"))))
            manualKeys()?.let { keys ->
                edit.putString("manual_order", JSONArray(orderByKeys(ordered, keys).map { it.key.asStoreKey() }).toString())
            }
            if (ordered.none { it.key.asStoreKey() == preferences.getString("cursor", null) }) {
                edit.remove("cursor").remove("position")
            }
        }
        edit.apply()
    }

    companion object { private val lock = Any() }
}
