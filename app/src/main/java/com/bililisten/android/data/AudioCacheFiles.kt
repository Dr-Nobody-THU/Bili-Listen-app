package com.bililisten.android.data

import java.io.File
import java.io.IOException

/** Only complete, atomically published files are eligible for playback. */
internal class AudioCacheFiles(private val root: File) {
    fun file(item: QueueItem): File {
        val directory = (item.collectionId ?: "single").replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(root, directory), "${item.key.asStoreKey().replace(':', '_')}.m4a")
    }

    fun cached(item: QueueItem): File? = file(item).takeIf { it.isFile && it.length() > 0L }

    fun completedFiles(): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "m4a" }.toList()
    fun relativePath(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    fun retainCollection(playback: PlannedPlayback, retained: Set<File>): Int {
        // Only prune this collection, never another collection's cached listening window.
        val candidates = if (playback.queue.collectionId == null || playback.queue.collectionId == SpecialFavorites.ID) playback.queue.items.map(::file)
            else file(playback.plan.item).parentFile?.listFiles().orEmpty().toList()
        return candidates
            .count { it.isFile && it.extension == "m4a" && it !in retained && it.delete() }
    }

    fun commit(temp: File, destination: File, expectedBytes: Long) {
        val size = temp.length()
        if (size <= 0 || (expectedBytes >= 0 && size != expectedBytes)) {
            throw IOException("音频下载不完整，请在 Wi-Fi 下重试")
        }
        if (!temp.renameTo(destination)) throw IOException("无法保存音频缓存")
    }

    fun trim(protectedFiles: Set<File>, maxBytes: Long): Int {
        val files = root.walkTopDown().filter { it.isFile && it.extension == "m4a" }.toList()
        var total = files.sumOf { it.length() }
        var deleted = 0
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (file in protectedFiles) continue
            val size = file.length()
            if (file.delete()) { total -= size; deleted++ }
        }
        return deleted
    }
}
