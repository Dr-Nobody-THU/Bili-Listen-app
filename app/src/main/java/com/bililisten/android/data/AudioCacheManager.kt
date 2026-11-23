package com.bililisten.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

enum class CachePhase { NOT_CACHED, WAITING_WIFI, QUEUED, DOWNLOADING, CACHED, FAILED }

data class AudioCacheEntry(
    val phase: CachePhase = CachePhase.NOT_CACHED,
    val bytes: Long = 0L,
    val totalBytes: Long = -1L,
    val error: String? = null,
)

data class AudioCacheSnapshot(
    val entries: Map<String, AudioCacheEntry> = emptyMap(),
    val wifiAvailable: Boolean = false,
    val targetCount: Int = 0,
    val readyTargetCount: Int = 0,
    val autoCacheEnabled: Boolean = false,
) {
    val cachedCount: Int get() = entries.values.count { it.phase == CachePhase.CACHED }
    val cachedBytes: Long get() = entries.values.filter { it.phase == CachePhase.CACHED }.sumOf { it.bytes }
}

data class AutoCacheMaintenanceResult(
    val wifiAvailable: Boolean,
    val targetCount: Int,
    val cachedCount: Int,
    val downloadedCount: Int,
    val deletedCount: Int,
)

class AudioCacheManager(context: Context, private val audioResolver: BilibiliAudioResolver) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val files = AudioCacheFiles(File(context.filesDir, "audio_cache"))
    private val retention = CacheRetentionStore(context, files)
    private val transfers = ConcurrentHashMap<String, AudioCacheEntry>()

    fun cachedAudioUri(item: QueueItem): Uri? = files.cached(item)?.let(Uri::fromFile)

    fun invalidate(item: QueueItem) {
        files.file(item).delete()
        transfers[item.key.asStoreKey()] = AudioCacheEntry(CachePhase.FAILED, error = "缓存音频无法读取，请连接 Wi-Fi 重试")
    }

    fun updateFavorites(favorites: List<FavoriteCollection>, now: Long, cleanup: Boolean = false): Int =
        retention.reconcile(favorites, now, deleteExpired = cleanup)

    fun snapshot(playback: PlannedPlayback, enabled: Boolean, canCache: (QueueItem) -> Boolean): AudioCacheSnapshot {
        val wifi = isWifiConnected()
        val targets = QueueOrdering.cacheWindow(playback).filter(canCache).map { it.key.asStoreKey() }.toSet()
        val entries = playback.queue.items.associate { item ->
            val key = item.key.asStoreKey()
            val cached = files.cached(item)
            val transfer = transfers[key]
            key to when {
                cached != null -> AudioCacheEntry(CachePhase.CACHED, cached.length(), cached.length())
                !canCache(item) -> AudioCacheEntry()
                transfer?.phase == CachePhase.DOWNLOADING && !wifi -> AudioCacheEntry(CachePhase.WAITING_WIFI)
                transfer != null -> transfer
                key in targets -> AudioCacheEntry(if (wifi) CachePhase.QUEUED else CachePhase.WAITING_WIFI)
                else -> AudioCacheEntry()
            }
        }
        return AudioCacheSnapshot(entries, wifi, targets.size, targets.count { entries[it]?.phase == CachePhase.CACHED }, enabled)
    }

    fun maintainWindow(playback: PlannedPlayback, previous: QueueItem? = null,
        canCache: (QueueItem) -> Boolean, onCached: (QueueItem) -> Unit = {}): AutoCacheMaintenanceResult {
        val targets = QueueOrdering.cacheWindow(playback).filter(canCache)
        // Unfavoriting stops new work; its existing files keep their separate 30-day grace period.
        if (targets.isEmpty()) return AutoCacheMaintenanceResult(isWifiConnected(), 0, 0, 0, 0)
        val protectedFiles = (targets + listOfNotNull(previous)).map { files.file(it) }.toSet()
        var deleted = if (!Thread.currentThread().isInterrupted) files.retainCollection(playback, protectedFiles) else 0
        transfers.keys.removeAll { key -> playback.queue.items.any { it.key.asStoreKey() == key && files.file(it) !in protectedFiles } }
        var downloaded = 0
        for (item in targets) {
            if (Thread.currentThread().isInterrupted) break
            if (files.cached(item) != null) continue
            val network = wifiNetwork() ?: break
            val key = item.key.asStoreKey()
            try {
                transfers[key] = AudioCacheEntry(CachePhase.DOWNLOADING)
                downloadItem(item, network) { canCache(item) }
                transfers.remove(key)
                downloaded++
                onCached(item)
            } catch (exception: Exception) {
                if (isCacheTransferCancelled(exception) || wifiNetwork() != network) {
                    transfers.remove(key)
                    break
                }
                transfers[key] = AudioCacheEntry(CachePhase.FAILED, error = exception.message ?: "缓存失败，请重试")
                // One unavailable video must not prevent caching the remaining items.
            }
        }
        deleted += if (!Thread.currentThread().isInterrupted) {
            files.trim(protectedFiles, MAX_CACHE_BYTES)
        } else 0
        return AutoCacheMaintenanceResult(isWifiConnected(), targets.size, targets.count { files.cached(it) != null }, downloaded, deleted)
    }

    fun isWifiConnected(): Boolean = wifiNetwork() != null

    private fun wifiNetwork(): Network? {
        val network = connectivity?.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        return network.takeIf {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
    }

    private fun ensureDownloadAllowed(network: Network, canCache: () -> Boolean) {
        if (!canCache()) throw CancellationException("已取消收藏，停止缓存")
        checkCacheTransferAllowed { wifiNetwork() == network }
    }

    private fun downloadItem(item: QueueItem, network: Network, canCache: () -> Boolean) {
        ensureDownloadAllowed(network, canCache)
        // Both API and audio requests use the captured network, never a cellular fallback.
        val audio = audioResolver.resolve(item, network)
        ensureDownloadAllowed(network, canCache)
        val destination = files.file(item)
        val temp = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        val connection = (network.openConnection(URL(audio.selected.url)) as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Referer", "https://www.bilibili.com/")
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "音频下载失败：HTTP ${connection.responseCode}" }
            val expected = connection.contentLengthLong
            var received = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureDownloadAllowed(network, canCache)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        transfers[item.key.asStoreKey()] = AudioCacheEntry(CachePhase.DOWNLOADING, received, expected)
                    }
                    output.fd.sync()
                }
            }
            ensureDownloadAllowed(network, canCache)
            files.commit(temp, destination, expected)
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }

    companion object {
        // Keep the just-left item as well as the current item and the next five.
        private const val MAX_CACHE_BYTES = 1024L * 1024L * 1024L
    }
}

/** Switching tracks/retrying cancels work; cancellation is not a Wi-Fi or cache failure. */
internal fun checkCacheTransferAllowed(networkAvailable: () -> Boolean) {
    if (Thread.currentThread().isInterrupted) throw CancellationException("缓存任务已取消")
    if (!networkAvailable()) throw IOException("Wi-Fi 已断开，缓存已暂停")
}

internal fun isCacheTransferCancelled(error: Exception): Boolean =
    Thread.currentThread().isInterrupted || error is CancellationException ||
        (error is InterruptedIOException && error !is SocketTimeoutException)
