package com.bililisten.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A bounded, per-collection MRU. Looking at a queue never changes its listening history. */
internal class RecentPlaybackStore(context: Context, private val legacy: ListenStateStore) {
    private val prefs = context.getSharedPreferences("recent_collection_progress", Context.MODE_PRIVATE)

    fun load(queue: ResolvedQueue): List<PlaybackProgress> = synchronized(lock) {
        val id = queue.collectionId ?: "single:${queue.items.first().key.asStoreKey()}"
        val raw = prefs.getString(id, null)
        if (raw == null) {
            val migrated = queue.items.mapNotNull { legacy.loadProgress(it.key) }
                .sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(LIMIT)
            write(id, migrated)
            legacy.clearCollectionProgress(queue)
            migrated
        } else runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                PlaybackProgress(ContentKey(item.getString("bvid"), item.getLong("cid")),
                    item.getLong("positionMs"), item.getLong("durationMs"),
                    item.getBoolean("finished"), item.getLong("updatedAt"))
            }.distinctBy { it.key }.take(LIMIT)
        }.getOrDefault(emptyList())
    }

    fun save(queue: ResolvedQueue, progress: PlaybackProgress) = synchronized(lock) {
        val recent = listOf(progress) + load(queue).filter { it.key != progress.key }.take(LIMIT - 1)
        write(queue.collectionId ?: "single:${queue.items.first().key.asStoreKey()}", recent)
    }

    fun clear(queue: ResolvedQueue, keepKey: ContentKey?) = synchronized(lock) {
        val keep = load(queue).filter { it.key == keepKey }
        write(queue.collectionId ?: "single:${queue.items.first().key.asStoreKey()}", keep)
    }

    private fun write(id: String, entries: List<PlaybackProgress>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("bvid", entry.key.bvid).put("cid", entry.key.cid)
                .put("positionMs", entry.positionMs).put("durationMs", entry.durationMs)
                .put("finished", entry.finished).put("updatedAt", entry.updatedAt))
        }
        prefs.edit().putString(id, array.toString()).apply()
    }

    companion object {
        private val lock = Any()
        private const val LIMIT = 3
    }
}
