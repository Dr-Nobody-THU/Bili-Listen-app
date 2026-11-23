package com.bililisten.android.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Expire resume state only. Listening totals, audio files and collection ordering are independent. */
internal class ResumeRetentionStore(context: Context) {
    private val retention = context.getSharedPreferences("unfavorited_resume_retention", Context.MODE_PRIVATE)
    private val app = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    private val recent = context.getSharedPreferences("recent_collection_progress", Context.MODE_PRIVATE)
    private val sessions = context.getSharedPreferences("collection_sessions", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE)
    private val widget = context.getSharedPreferences("playback_widget", Context.MODE_PRIVATE)

    fun removed(playback: PlannedPlayback, now: Long) = synchronized(lock) {
        val keys = JSONArray(playback.queue.items.map { it.key.asStoreKey() })
        val record = JSONObject().put("removedAt", now).put("keys", keys)
        retention.edit { putString(idOf(playback), record.toString()) }
    }

    fun refavorited(id: String) = synchronized(lock) { retention.edit { remove(id) } }

    fun cleanup(now: Long, specialFavorite: FavoriteCollection?): Set<String> = synchronized(lock) {
        val favoriteRecords = app.all.filter { (key, value) -> key.startsWith("favorite:") && value is String && value.isNotBlank() }
        val favorites = favoriteRecords.values.mapNotNull {
            (it as? String)?.let { raw -> runCatching { FavoriteJsonCodec.decode(raw) }.getOrNull() }
        } + listOfNotNull(specialFavorite)
        val favoriteIds = favoriteRecords.keys.map { it.removePrefix("favorite:") }.toSet() + listOfNotNull(specialFavorite?.id)
        val favoriteKeys = favorites.flatMap { it.playback.queue.items }.map { it.key.asStoreKey() }.toSet()
        val last = decode(app.getString("last_playback", null))
        val widgetPlayback = decode(widget.getString("playback", null))
        val snapshots = listOfNotNull(last, widgetPlayback).groupBy(::idOf)
        val records = retention.all.mapValues { (_, raw) ->
            (raw as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
        val resumeIds = recent.all.filterValues { it is String && it != "[]" }.keys
        val knownIds = records.keys + resumeIds + sessions.all.keys + snapshots.keys
        val expired = mutableSetOf<String>()
        val keysToClear = mutableSetOf<String>()
        retention.edit {
            for (id in knownIds) {
                if (id in favoriteIds) {
                    remove(id)
                    continue
                }
                val record = records[id]
                val removedAt = record?.optLong("removedAt", -1) ?: -1
                if (removedAt < 0) {
                    // Older versions did not retain an unfavorite date. Give those records a full grace period.
                    val keys = snapshots[id].orEmpty().flatMap { it.queue.items }.map { it.key.asStoreKey() }
                    putString(id, JSONObject().put("removedAt", now).put("keys", JSONArray(keys)).toString())
                } else if (now >= removedAt && now - removedAt >= RETENTION_MS) {
                    expired += id
                    val keys = record?.optJSONArray("keys") ?: JSONArray()
                    for (index in 0 until keys.length()) keysToClear += keys.optString(index)
                    snapshots[id].orEmpty().flatMap { it.queue.items }.forEach { keysToClear += it.key.asStoreKey() }
                    val positions = runCatching { JSONArray(recent.getString(id, "[]")) }.getOrDefault(JSONArray())
                    for (index in 0 until positions.length()) {
                        positions.optJSONObject(index)?.let { keysToClear += "${it.optString("bvid")}:${it.optLong("cid")}" }
                    }
                    remove(id)
                }
            }
        }
        if (expired.isNotEmpty()) {
            // An empty MRU also prevents re-importing shared legacy positions from another collection.
            recent.edit { expired.forEach { putString(it, "[]") } }
            sessions.edit { expired.forEach(::remove) }
            // Very old versions used global content keys. Preserve keys still owned by a favorite.
            legacy.edit { (keysToClear - favoriteKeys).forEach(::remove) }
            if (last != null && idOf(last) in expired) app.edit { remove("last_playback") }
            if (widgetPlayback != null && idOf(widgetPlayback) in expired) widget.edit { remove("playback"); remove("duration") }
        }
        expired
    }

    private fun decode(raw: String?): PlannedPlayback? = raw?.let { runCatching { PlaybackJsonCodec.decode(it) }.getOrNull() }
    private fun idOf(playback: PlannedPlayback) = playback.queue.collectionId ?: "single:${playback.plan.item.key.asStoreKey()}"

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1_000
        private val lock = Any()
    }
}
