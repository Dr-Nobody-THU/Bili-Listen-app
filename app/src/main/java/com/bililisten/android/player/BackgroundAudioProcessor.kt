package com.bililisten.android.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.BackgroundSound
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** One PCM output owns both voices: pause, focus loss, routing and playback fades stay in sync. */
@UnstableApi
internal class BackgroundAudioProcessor : BaseAudioProcessor() {
    private data class Request(val settings: BackgroundAudioSettings, val loop: ShortArray?)
    @Volatile private var requested = Request(BackgroundAudioSettings(), null)
    private var applied: Request? = null
    private val voices = mutableListOf<Voice>()

    fun update(settings: BackgroundAudioSettings, loop: ShortArray? = null) {
        require(settings.sound.asset == null || !settings.enabled || settings.volume == 0f || (loop != null && loop.size > 1))
        requested = Request(settings.normalized(), loop)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Remain in the pipeline while disabled so the switch takes effect without re-preparing audio.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 also feeds EMPTY_BUFFER while warming up/draining the processor chain.
        // Before the first allocation replaceOutputBuffer(0) returns that same singleton:
        // copying it into itself throws, even though there are zero bytes to copy.
        // An empty input must also leave any pending output untouched.
        if (!inputBuffer.hasRemaining()) return
        val rate = inputAudioFormat.sampleRate
        val frameBytes = inputAudioFormat.bytesPerFrame
        require(inputBuffer.remaining() % frameBytes == 0) { "Incomplete PCM frame" }
        val nextRequest = requested
        if (nextRequest != applied) {
            voices.forEach { it.rampTo(0f, rate) }
            val settings = nextRequest.settings
            if (settings.enabled && settings.volume > 0f) {
                val voice = voices.find { it.sound == settings.sound } ?: Voice(
                    settings.sound,
                    nextRequest.loop?.let(::LoopSource) ?: NoiseSource(settings.sound),
                ).also(voices::add)
                voice.rampTo(settings.volume, rate)
            }
            applied = nextRequest
        }
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (voices.isEmpty()) {
            output.put(inputBuffer) // Disabled: exact original PCM, including full-scale samples.
        } else while (inputBuffer.hasRemaining()) {
            var background = 0f
            var totalGain = 0f
            var voiceIndex = 0
            while (voiceIndex < voices.size) {
                val voice = voices[voiceIndex++]
                voice.advanceGain()
                background += voice.source.next(rate) * voice.gain
                totalGain += voice.gain
            }
            val knee = (totalGain * 0.15f).coerceIn(0f, 0.15f)
            repeat(inputAudioFormat.channelCount) {
                val original = inputBuffer.short
                if (totalGain <= 0f) output.putShort(original) else {
                    val mixed = original / 32768f + background
                    // A progressively introduced soft knee prevents clipping without a hard
                    // change in the main program's gain when background audio is toggled.
                    val magnitude = abs(mixed)
                    val threshold = 1f - knee
                    val safe = if (magnitude <= threshold) mixed else {
                        val excess = magnitude - threshold
                        val limited = threshold + excess / (1f + excess / knee)
                        if (mixed < 0f) -limited else limited
                    }
                    output.putShort((safe * 32768f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }
        voices.removeAll { it.target == 0f && it.gain == 0f }
        output.flip()
    }

    // A seek, pause, speed change or cache handoff flushes PCM, not the ambience's loop position.
    override fun onReset() {
        voices.clear()
        applied = null // Reapply the desired setting after an actual renderer reset.
    }

    private class Voice(val sound: BackgroundSound, val source: Source) {
        var gain = 0f
        var target = 0f
        private var step = 0f
        private var remaining = 0
        fun rampTo(value: Float, rate: Int) {
            target = value
            remaining = (rate * 0.4f).toInt().coerceAtLeast(1)
            step = (target - gain) / remaining
        }
        fun advanceGain() {
            if (remaining > 0) {
                remaining--
                gain = if (remaining == 0) target else gain + step
            }
        }
    }

    internal fun interface Source { fun next(outputRate: Int): Float }

    /** Interpolates a prepared loop, including the last-to-first sample, with no decoder restart. */
    internal class LoopSource(private val pcm: ShortArray) : Source {
        private var position = 0.0
        init { require(pcm.size > 1) }
        override fun next(outputRate: Int): Float {
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val first = pcm[index].toFloat()
            val second = pcm[if (index + 1 == pcm.size) 0 else index + 1].toFloat()
            val sample = (first + (second - first) * fraction) / 32768f
            position += SOURCE_RATE.toDouble() / outputRate
            if (position >= pcm.size) position %= pcm.size
            return sample
        }
    }

    /** Continuous, stateful generators: there is no file boundary or repeated short noise clip. */
    internal class NoiseSource(private val sound: BackgroundSound, seed: Int = System.nanoTime().toInt()) : Source {
        private var random = seed.takeUnless { it == 0 } ?: 0x35791357
        private val bands = FloatArray(12) { white() }
        private var sum = bands.sum()
        private var counter = 0
        private var brown = 0f
        private var dc = 0f
        private var previousRate = 0
        private var brownDecay = 0f
        private var brownStep = 0f
        private var dcStep = 0f

        init { require(sound.asset == null) }

        private fun white(): Float {
            random = random xor (random shl 13)
            random = random xor (random ushr 17)
            random = random xor (random shl 5)
            return random / 2147483648f
        }

        override fun next(outputRate: Int): Float {
            if (previousRate != outputRate) {
                previousRate = outputRate
                brownDecay = exp(-2.0 * Math.PI * 30.0 / outputRate).toFloat()
                brownStep = sqrt(1f - brownDecay * brownDecay) * 0.2f
                dcStep = (1.0 - exp(-2.0 * Math.PI * 8.0 / outputRate)).toFloat()
            }
            return when (sound) {
                BackgroundSound.WHITE -> white() * 0.2f
                BackgroundSound.PINK -> {
                    val band = Integer.numberOfTrailingZeros(++counter).coerceAtMost(bands.lastIndex)
                    val next = white()
                    sum += next - bands[band]
                    bands[band] = next
                    val raw = (sum + white()) * 0.065f
                    dc += dcStep * (raw - dc)
                    (raw - dc).coerceIn(-0.75f, 0.75f)
                }
                BackgroundSound.BROWN -> {
                    brown = brownDecay * brown + white() * brownStep
                    brown.coerceIn(-0.75f, 0.75f)
                }
                else -> error("A recorded sound needs a loop")
            }
        }
    }

    companion object { const val SOURCE_RATE = 22_050 }
}

/** Media3 normally places custom processors before Sonic. Place ambience after it instead. */
@UnstableApi
internal class BackgroundAudioProcessorChain(
    background: BackgroundAudioProcessor,
    private val delegate: AudioProcessorChain = DefaultAudioSink.DefaultAudioProcessorChain(),
) : AudioProcessorChain by delegate {
    private val processors = delegate.audioProcessors + background
    override fun getAudioProcessors(): Array<AudioProcessor> = processors
}
