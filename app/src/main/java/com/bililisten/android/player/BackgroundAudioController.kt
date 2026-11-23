package com.bililisten.android.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.BackgroundSound
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Loads only the chosen packaged loop off the playback/UI threads. No network or second player. */
@UnstableApi
internal class BackgroundAudioController(
    private val processor: BackgroundAudioProcessor,
    private val loadLoop: (BackgroundSound) -> ShortArray,
    private val onError: (Exception) -> Unit,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val cache = LinkedHashMap<BackgroundSound, ShortArray>(2, 0.75f, true)
    private var current = BackgroundAudioSettings()
    private var pending: BackgroundSound? = null
    private var generation = 0L
    private var closed = false

    @Synchronized fun update(settings: BackgroundAudioSettings) {
        if (closed) return
        current = settings.normalized()
        if (!current.enabled || current.volume == 0f || current.sound.asset == null) {
            generation++
            pending = null
            processor.update(current)
            return
        }
        cache[current.sound]?.let {
            generation++
            pending = null
            processor.update(current, it)
            return
        }
        if (pending == current.sound) return // A slider adjustment must not start another disk read.
        val sound = current.sound
        pending = sound
        val ticket = ++generation
        executor.execute {
            if (synchronized(this) { closed || ticket != generation }) return@execute
            try {
                val pcm = loadLoop(sound)
                synchronized(this) {
                    if (closed || ticket != generation) return@execute
                    cache[sound] = pcm
                    while (cache.size > 2) cache.remove(cache.keys.first())
                    pending = null
                    processor.update(current, pcm)
                }
            } catch (error: Exception) {
                synchronized(this) {
                    if (closed || ticket != generation) return@execute
                    pending = null
                    processor.update(current.copy(enabled = false))
                }
                onError(error)
            }
        }
    }

    @Synchronized fun close() {
        closed = true
        generation++
        processor.update(current.copy(enabled = false))
        cache.clear()
        executor.shutdownNow()
    }

    companion object {
        fun loadPackagedLoop(context: Context, sound: BackgroundSound): ShortArray {
            val asset = requireNotNull(sound.asset)
            val bytes = context.assets.open("background/$asset.pcm").use { it.readBytes() }
            require(bytes.size in 4..(BackgroundAudioProcessor.SOURCE_RATE * 60 * 2) && bytes.size % 2 == 0) {
                "Invalid background audio asset: $asset"
            }
            val pcm = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
            return pcm
        }
    }
}
