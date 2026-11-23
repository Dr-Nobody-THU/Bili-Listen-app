package com.bililisten.android.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.BackgroundSound
import com.google.common.collect.ImmutableList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundAudioTest {
    private val rate = 48_000
    private val enabled = BackgroundAudioSettings(true, BackgroundSound.RAIN, 1f)
    private val loop = ShortArray(22_050) { (sin(2 * Math.PI * 440 * it / 22_050) * 5000).toInt().toShort() }

    private fun processor(channels: Int = 1) = BackgroundAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun pcm(samples: ShortArray) = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
        asShortBuffer().put(samples)
    }

    private fun process(processor: BackgroundAudioProcessor, samples: ShortArray): ShortArray {
        val input = pcm(samples)
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output
        return ShortArray(output.remaining() / 2).also { output.asShortBuffer().get(it); output.position(output.limit()) }
    }

    @Test
    @Config(sdk = [28, 36])
    fun disabledBackgroundAcceptsMedia3EmptyBufferBeforeFirstAudioAndAfterReset() {
        val processor = processor()
        repeat(2) {
            // Media3 feeds this shared buffer while Sonic/decoder has no output yet.
            processor.queueInput(AudioProcessor.EMPTY_BUFFER)
            assertFalse(processor.output.hasRemaining())
            val dry = shortArrayOf(-10000, 0, 10000)
            assertArrayEquals(dry, process(processor, dry))
            processor.reset()
            processor.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
            processor.flush()
        }
    }

    @Test fun emptyInputPreservesAudioThatHasNotYetBeenRead() {
        val processor = processor()
        val dry = shortArrayOf(-30000, -1, 0, 1, 30000)
        processor.queueInput(pcm(dry))
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
        val output = processor.output
        assertEquals(dry.size * 2, output.remaining())
        assertArrayEquals(dry, ShortArray(dry.size).also { output.asShortBuffer().get(it) })
    }

    @Test
    @Config(sdk = [28, 36])
    fun disabledMedia3PipelineMatchesOriginalAudioAcrossWarmupSpeedAndSourceChanges() {
        fun render(withBackgroundProcessor: Boolean): List<Short> {
            val chain = if (withBackgroundProcessor) BackgroundAudioProcessorChain(BackgroundAudioProcessor())
                else DefaultAudioSink.DefaultAudioProcessorChain()
            val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))
            val result = mutableListOf<Short>()
            fun drain() {
                val output = pipeline.output
                while (output.hasRemaining()) result += output.short
            }
            for ((sampleRate, speed) in listOf(48000 to 1f, 48000 to 1.5f, 44100 to 2f, 44100 to 1f)) {
                chain.applyPlaybackParameters(PlaybackParameters(speed))
                pipeline.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
                pipeline.flush()
                pipeline.queueInput(AudioProcessor.EMPTY_BUFFER)
                drain()
                repeat(60) { chunk ->
                    val input = pcm(ShortArray(128) { sample -> ((chunk * 128 + sample) % 1000 - 500).toShort() })
                    if (!pipeline.isOperational) {
                        // DefaultAudioSink bypasses its pipeline when every processor is inactive.
                        while (input.hasRemaining()) result += input.short
                    } else {
                        var iterations = 0
                        while (input.hasRemaining()) {
                            assertTrue("Pipeline must make progress", ++iterations < 100)
                            pipeline.queueInput(input)
                            drain()
                        }
                    }
                }
                if (pipeline.isOperational) {
                    pipeline.queueEndOfStream()
                    repeat(100) { if (!pipeline.isEnded) drain() }
                    assertTrue(pipeline.isEnded)
                }
                // Renderer reset when moving from an online source to a cached source/next track.
                pipeline.reset()
            }
            return result
        }
        val original = render(false)
        assertTrue(original.isNotEmpty())
        assertEquals(original, render(true))
    }

    @Test fun disabledIsBitExactAndSwitchOffFadesBeforeReturningToOriginalPcm() {
        val processor = processor()
        val dry = shortArrayOf(Short.MIN_VALUE, -10000, 0, 10000, Short.MAX_VALUE)
        assertArrayEquals(dry, process(processor, dry))
        processor.update(enabled, ShortArray(100) { 8000 })
        val ramp = process(processor, ShortArray(rate))
        assertTrue(ramp.first() < 2)
        assertEquals(8000, ramp.last().toInt())
        processor.update(enabled.copy(enabled = false))
        val fade = process(processor, ShortArray(rate))
        assertTrue(fade.first() > 7900)
        assertEquals(0, fade.last().toInt())
        assertTrue(fade.drop(rate / 2).all { it == 0.toShort() })
        assertArrayEquals(dry, process(processor, dry))
    }

    @Test fun buffersSeeksAndStereoChannelsDoNotRestartOrBreakTheLoop() {
        val whole = processor().apply { update(enabled, loop) }
        val expected = process(whole, ShortArray(rate * 3))
        val split = processor().apply { update(enabled, loop) }
        val first = process(split, ShortArray(17_003))
        split.flush() // A main-program seek/cache handoff discards PCM but preserves ambience time.
        val second = process(split, ShortArray(rate * 3 - first.size))
        assertArrayEquals(expected, first + second)
        val stereo = processor(2).apply { update(enabled, loop) }
        val stereoOutput = process(stereo, ShortArray(rate * 3 * 2))
        expected.indices.forEach { frame ->
            assertEquals(expected[frame], stereoOutput[frame * 2])
            assertEquals(expected[frame], stereoOutput[frame * 2 + 1])
        }
        val afterRamp = expected.drop(rate)
        assertTrue("A loop seam must not introduce an isolated step", afterRamp.zipWithNext().maxOf { abs(it.second - it.first) } < 400)
    }

    @Test fun sceneChangesAndRapidReversalCrossfadeWithoutAnAbruptStep() {
        val processor = processor().apply { update(enabled, ShortArray(100) { 8000 }) }
        process(processor, ShortArray(rate))
        processor.update(enabled.copy(sound = BackgroundSound.TRAIN), ShortArray(100) { -8000 })
        val firstHalf = process(processor, ShortArray(rate / 5))
        assertTrue(firstHalf.first() > 7900)
        processor.update(enabled, ShortArray(100) { 8000 })
        val reversed = process(processor, ShortArray(rate))
        assertTrue(abs(firstHalf.last() - reversed.first()) < 4)
        assertTrue((firstHalf + reversed).asList().zipWithNext().maxOf { abs(it.second - it.first) } < 4)
        assertEquals(8000, reversed.last().toInt())
    }

    @Test fun fullScaleInputDoesNotOverflowAndBackgroundCannotOutliveMainPcm() {
        val processor = processor().apply { update(enabled, ShortArray(100) { 30000 }) }
        val output = process(processor, ShortArray(rate) { Short.MAX_VALUE })
        assertTrue(output.all { it > 0 })
        assertTrue(output.min() > 28_000)
        processor.queueEndOfStream()
        assertFalse(processor.output.hasRemaining())
        assertTrue(processor.isEnded)
    }

    @Test fun threeNoiseColorsAreContinuousBoundedAndHaveDifferentSpectralBalance() {
        val roughness = listOf(BackgroundSound.WHITE, BackgroundSound.PINK, BackgroundSound.BROWN).map { sound ->
            val source = BackgroundAudioProcessor.NoiseSource(sound, seed = 1234567)
            val samples = FloatArray(rate * 4) { source.next(rate) }
            assertTrue(samples.all { it.isFinite() && abs(it) <= .75f })
            val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
            assertTrue("Audible noise level: $sound", rms in .04..0.20)
            val delta = sqrt((1 until samples.size).sumOf { val d = samples[it] - samples[it - 1]; d.toDouble() * d } / samples.size)
            delta / rms
        }
        assertTrue(roughness[0] > roughness[1] * 1.3)
        assertTrue(roughness[1] > roughness[2] * 2)
    }

    @Test
    @Config(sdk = [28, 36])
    fun actualMedia3PipelineChangesProgramSpeedButNotBackgroundPitchOrSpeed() {
        fun render(speed: Float): ShortArray {
            val processor = BackgroundAudioProcessor().apply { update(enabled, loop) }
            val chain = BackgroundAudioProcessorChain(processor)
            chain.applyPlaybackParameters(PlaybackParameters(speed))
            val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))
            pipeline.configure(AudioProcessor.AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
            pipeline.flush()
            val input = pcm(ShortArray(rate * 4))
            val result = mutableListOf<Short>()
            fun drain() {
                val output = pipeline.output
                while (output.remaining() >= 2) result += output.short
            }
            while (input.hasRemaining()) { pipeline.queueInput(input); drain() }
            pipeline.queueEndOfStream()
            repeat(100) { if (!pipeline.isEnded) drain() }
            assertTrue(pipeline.isEnded)
            pipeline.reset()
            return result.toShortArray()
        }
        val normal = render(1f)
        val fast = render(2f)
        assertEquals(normal.size / 2.0, fast.size.toDouble(), 600.0)
        fun frequency(data: ShortArray): Double {
            val start = rate
            val crossings = (start + 1 until data.size).count { data[it - 1] < 0 && data[it] >= 0 }
            return crossings * rate.toDouble() / (data.size - start)
        }
        assertEquals(440.0, frequency(normal), 2.0)
        assertEquals(440.0, frequency(fast), 2.0)
    }

    @Test fun packagedRecordingsAllHaveUsableLevelsNoSilentWindowsAndCompleteCredits() {
        val context = RuntimeEnvironment.getApplication()
        val credits = context.assets.open("background/SOURCES.txt").bufferedReader().use { it.readText() }
        for (sound in BackgroundSound.entries.filter { it.asset != null }) {
            val pcm = BackgroundAudioController.loadPackagedLoop(context, sound)
            assertTrue(pcm.size > 22_050 * 5)
            val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size) / 32768
            assertEquals("Comparable average level: $sound", .1, rms, .001)
            assertTrue(pcm.maxOf { abs(it.toInt()) } < 24_576)
            assertEquals(0.0, pcm.sumOf { it.toDouble() } / pcm.size, 2.0)
            for (offset in 0 until pcm.size - 4410 step 4410) {
                assertTrue("No silent 200 ms at $offset in $sound", (offset until offset + 4410).any { abs(pcm[it].toInt()) > 32 })
            }
            assertTrue(credits.contains(sound.asset!!))
        }
        assertTrue(credits.contains("https://creativecommons.org/licenses/by/4.0/"))
    }

    private class QueuedExecutor : AbstractExecutorService() {
        val jobs = mutableListOf<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { jobs += command }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return jobs.toMutableList().also { jobs.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = stopped
        fun runNext() = jobs.removeAt(0).run()
    }

    @Test fun disablingWhileAssetLoadsPreventsTheOldLoadFromEnablingAudioAgain() {
        val executor = QueuedExecutor()
        val processor = processor()
        lateinit var controller: BackgroundAudioController
        controller = BackgroundAudioController(processor, loadLoop = {
            controller.update(enabled.copy(enabled = false))
            loop
        }, onError = { throw it }, executor = executor)
        controller.update(enabled)
        controller.update(enabled.copy(volume = .3f))
        assertEquals("Volume changes reuse the pending load", 1, executor.jobs.size)
        executor.runNext()
        assertArrayEquals(ShortArray(rate), process(processor, ShortArray(rate)))
        controller.close()
    }

    @Test fun aFailedAssetLoadLeavesTheMainProgramUntouched() {
        val processor = processor()
        val executor = QueuedExecutor()
        var errors = 0
        val controller = BackgroundAudioController(processor, { error("broken asset") }, { errors++ }, executor)
        controller.update(enabled)
        executor.runNext()
        assertEquals(1, errors)
        val program = ShortArray(rate) { (it % 30000).toShort() }
        assertArrayEquals(program, process(processor, program))
        controller.close()
    }
}
