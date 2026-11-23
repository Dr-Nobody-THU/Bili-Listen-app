package com.bililisten.android.data

import android.content.Context

/** Separate from resume positions: seeking, completing a track and clearing its position do not affect totals. */
internal class ListeningTimeStore(context: Context) {
    private val prefs = context.getSharedPreferences("collection_listening_time", Context.MODE_PRIVATE)

    fun totalMs(collectionId: String): Long = prefs.getLong(collectionId, 0L).coerceAtLeast(0L)

    /** Include every recorded collection, independent of favorites or the current queue. */
    fun totalMs(): Long = prefs.all.values.fold(0L) { total, value ->
        val elapsed = (value as? Long)?.coerceAtLeast(0L) ?: 0L
        total + elapsed.coerceAtMost(Long.MAX_VALUE - total)
    }

    fun add(collectionId: String, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        synchronized(lock) {
            val current = totalMs(collectionId)
            prefs.edit().putLong(collectionId, current + elapsedMs.coerceAtMost(Long.MAX_VALUE - current)).apply()
        }
    }

    private companion object { val lock = Any() }
}

internal fun formatListeningTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000
    val minutes = seconds / 60
    return when {
        minutes >= 60 -> "${minutes / 60}小时${minutes % 60}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "${seconds}秒"
    }
}
