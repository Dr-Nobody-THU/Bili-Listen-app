package com.bililisten.android.data

import android.content.Context
import androidx.core.content.edit

/** Date an existing file loses its last favorite owner; playback/file access never renews it. */
internal class CacheRetentionStore(context: Context, private val files: AudioCacheFiles) {
    private val preferences = context.getSharedPreferences("unfavorited_cache_retention", Context.MODE_PRIVATE)

    fun reconcile(favorites: List<FavoriteCollection>, now: Long, deleteExpired: Boolean): Int = synchronized(lock) {
        val ownedFiles = favorites.flatMap { it.playback.queue.items }.map(files::file).toSet()
        val ownedDirectories = favorites.filter { it.id != SpecialFavorites.ID && it.playback.queue.collectionId != null }
            .mapNotNull { files.file(it.playback.plan.item).parentFile }.toSet()
        val completed = files.completedFiles()
        val livePaths = completed.map(files::relativePath).toSet()
        var deleted = 0
        preferences.edit {
            preferences.all.keys.filterNot { it in livePaths }.forEach(::remove)
            for (file in completed) {
                val path = files.relativePath(file)
                if (file in ownedFiles || file.parentFile?.let { it in ownedDirectories } == true) {
                    remove(path)
                } else {
                    val since = preferences.getLong(path, -1L)
                    if (since < 0L) {
                        // Legacy files have no reliable unfavorite date: grant a full month from migration.
                        putLong(path, now)
                    } else if (deleteExpired && now >= since && now - since >= RETENTION_MS && file.delete()) {
                        remove(path)
                        deleted++
                    }
                }
            }
        }
        deleted
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private val lock = Any()
    }
}
