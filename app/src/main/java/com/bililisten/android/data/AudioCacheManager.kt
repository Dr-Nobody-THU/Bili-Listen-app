package com.bililisten.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AutoCacheMaintenanceResult(
    val wifiAvailable: Boolean,
    val targetCount: Int,
    val cachedCount: Int,
    val downloadedCount: Int,
    val deletedCount: Int,
)

class AudioCacheManager(
    private val context: Context,
    private val audioResolver: BilibiliAudioResolver,
) {
    private val cacheRoot = File(context.filesDir, CACHE_DIR_NAME)

    fun cachedAudioUri(item: QueueItem): Uri? {
        val collectionId = item.collectionId ?: return null
        val file = cacheFile(collectionId, item.key)
        return file.takeIf { it.isFile && it.length() > 0L }?.let(Uri::fromFile)
    }

    fun maintainWindow(playback: PlannedPlayback): AutoCacheMaintenanceResult {
        val collectionId = playback.queue.collectionId
        if (collectionId == null || playback.queue.items.isEmpty()) {
            return AutoCacheMaintenanceResult(
                wifiAvailable = isWifiConnected(),
                targetCount = 0,
                cachedCount = 0,
                downloadedCount = 0,
                deletedCount = 0,
            )
        }

        if (!isWifiConnected()) {
            return AutoCacheMaintenanceResult(
                wifiAvailable = false,
                targetCount = 0,
                cachedCount = 0,
                downloadedCount = 0,
                deletedCount = 0,
            )
        }

        val targets = cacheWindowItems(playback)
        val targetKeys = targets.map { it.key.asStoreKey() }.toSet()
        val collectionDir = collectionCacheDir(collectionId)
        val deletedCount = deleteFilesOutsideWindow(collectionDir, targetKeys)
        var cachedCount = targets.count { cacheFile(collectionId, it.key).isFile }
        var downloadedCount = 0

        for (item in targets) {
            val file = cacheFile(collectionId, item.key)
            if (file.isFile && file.length() > 0L) {
                continue
            }
            if (!isWifiConnected()) {
                break
            }
            downloadItem(item, file)
            downloadedCount += 1
            cachedCount += 1
        }

        return AutoCacheMaintenanceResult(
            wifiAvailable = true,
            targetCount = targets.size,
            cachedCount = cachedCount,
            downloadedCount = downloadedCount,
            deletedCount = deletedCount,
        )
    }

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun cacheWindowItems(playback: PlannedPlayback): List<QueueItem> {
        val items = playback.queue.items
        if (items.isEmpty()) {
            return emptyList()
        }
        val startIndex = playback.plan.startIndex.coerceIn(items.indices)
        val count = minOf(CACHE_WINDOW_SIZE, items.size)
        return (0 until count).map { offset ->
            items[(startIndex + offset) % items.size]
        }
    }

    private fun deleteFilesOutsideWindow(collectionDir: File, targetKeys: Set<String>): Int {
        if (!collectionDir.isDirectory) {
            return 0
        }
        var deletedCount = 0
        collectionDir.listFiles()?.forEach { file ->
            if (!file.isFile) {
                return@forEach
            }
            val key = file.nameWithoutExtension.replace('_', ':')
            if (key !in targetKeys && file.delete()) {
                deletedCount += 1
            }
        }
        return deletedCount
    }

    private fun downloadItem(item: QueueItem, destination: File) {
        val audio = audioResolver.resolve(item)
        val temp = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        if (temp.exists()) {
            temp.delete()
        }

        val connection = (URL(audio.selected.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Referer", BILIBILI_REFERER)
            setRequestProperty("User-Agent", BILIBILI_USER_AGENT)
        }

        try {
            val status = connection.responseCode
            check(status in 200..299) { "Audio cache download failed with HTTP $status." }
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (!isWifiConnected()) {
                            throw IllegalStateException("Wi-Fi disconnected during audio cache download.")
                        }
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (destination.exists()) {
                destination.delete()
            }
            check(temp.renameTo(destination)) { "Failed to finalize audio cache file." }
        } catch (exception: Exception) {
            temp.delete()
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheFile(collectionId: String, key: ContentKey): File {
        return File(collectionCacheDir(collectionId), "${key.asStoreKey().replace(':', '_')}.m4a")
    }

    private fun collectionCacheDir(collectionId: String): File {
        return File(cacheRoot, collectionId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    companion object {
        private const val CACHE_DIR_NAME = "audio_cache"
        private const val CACHE_WINDOW_SIZE = 6
        private const val BILIBILI_REFERER = "https://www.bilibili.com/"
        private const val BILIBILI_USER_AGENT = "Mozilla/5.0"
    }
}
