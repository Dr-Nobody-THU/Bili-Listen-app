package com.bililisten.android.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackVolumeFaderTest {
    private class GainPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var gain = 1f
        var requested = false
        var readiness = Player.STATE_READY
        val gainsAtPause = mutableListOf<Float>()

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_VOLUME, Player.COMMAND_SET_VOLUME,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE,
            ).build())
            .setPlaylist(listOf(MediaItemData.Builder("one").setMediaItem(MediaItem.fromUri("file:///one.m4a")).build()))
            .setCurrentMediaItemIndex(0).setPlaybackState(readiness).setVolume(gain)
            .setPlayWhenReady(requested, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()

        override fun handleSetVolume(volume: Float): ListenableFuture<*> {
            gain = volume
            return Futures.immediateVoidFuture()
        }
        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (!playWhenReady) gainsAtPause += gain
            requested = playWhenReady
            return Futures.immediateVoidFuture()
        }
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        fun signalState(state: Int) { readiness = state; invalidateState() }
    }

    private val player = GainPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun playingFader(): PlaybackVolumeFader = PlaybackVolumeFader(player, handler).also {
        it.requestPlayWhenReady(true)
        advance(400)
    }

    @Test fun fadeInWaitsForAudioAndPauseHappensOnlyAfterVolumeReachesZero() {
        player.signalState(Player.STATE_BUFFERING)
        val fader = PlaybackVolumeFader(player, handler)
        fader.requestPlayWhenReady(true)
        advance(1_000)
        assertEquals(0f, player.volume, 0.001f)
        player.signalState(Player.STATE_READY)
        advance(192)
        assertTrue(player.volume in 0.3f..0.7f)
        advance(208)
        assertEquals(1f, player.volume, 0.001f)
        fader.requestPlayWhenReady(false)
        assertFalse(fader.playRequested)
        assertTrue(player.isPlaying)
        advance(192)
        assertTrue(player.volume in 0.3f..0.7f)
        assertTrue(player.isPlaying)
        advance(208)
        assertFalse(player.playWhenReady)
        assertEquals(0f, player.gainsAtPause.last(), 0.001f)
        fader.close()
    }

    @Test fun quickResumeReversesTheCurrentGainAndCancelsTheOldPause() {
        val fader = playingFader()
        fader.requestPlayWhenReady(false)
        advance(176)
        val quiet = player.volume
        fader.requestPlayWhenReady(true)
        assertEquals(quiet, player.volume, 0.001f)
        advance(240) // Past the abandoned pause's deadline.
        assertTrue(player.isPlaying)
        assertTrue(player.volume > quiet)
        advance(160)
        assertTrue(player.isPlaying)
        assertEquals(1f, player.volume, 0.001f)
        fader.close()
    }

    @Test fun disablingFadesFinishesAPendingPauseAndSubsequentControlsAreImmediate() {
        val fader = playingFader()
        fader.requestPlayWhenReady(false)
        advance(96)
        fader.setEnabled(false)
        assertFalse(player.playWhenReady)
        advance(500)
        fader.requestPlayWhenReady(true)
        assertTrue(player.isPlaying)
        assertEquals(1f, player.volume, 0.001f)
        fader.requestPlayWhenReady(false)
        assertFalse(player.isPlaying)
        fader.close()
    }

    @Test fun externalPauseWhileBufferingCancelsFadeInWithoutResurrectingPlayback() {
        player.signalState(Player.STATE_BUFFERING)
        val fader = PlaybackVolumeFader(player, handler)
        fader.requestPlayWhenReady(true)
        player.pause() // ExoPlayer also pauses directly on audio-focus loss or a noisy route.
        player.signalState(Player.STATE_READY)
        advance(1_000)
        assertFalse(player.playWhenReady)
        assertFalse(fader.playRequested)
        assertEquals(1f, player.volume, 0.001f)
        fader.close()
    }

    @Test fun bufferingDuringFadeOutHonorsPauseInsteadOfRestartingWhenReady() {
        val fader = playingFader()
        fader.requestPlayWhenReady(false)
        advance(96)
        player.signalState(Player.STATE_BUFFERING)
        assertFalse(player.playWhenReady)
        player.signalState(Player.STATE_READY)
        advance(600)
        assertFalse(player.isPlaying)
        fader.close()
    }

    @Test fun changingTheSourceCancelsTheOldFadeOutBeforeStartingTheNextAudio() {
        val fader = playingFader()
        fader.requestPlayWhenReady(false)
        advance(160)
        fader.pauseImmediately()
        player.signalState(Player.STATE_BUFFERING)
        fader.requestPlayWhenReady(true)
        advance(600)
        assertTrue(player.playWhenReady)
        assertEquals(0f, player.volume, 0.001f)
        player.signalState(Player.STATE_READY)
        advance(400)
        assertTrue(player.isPlaying)
        assertEquals(1f, player.volume, 0.001f)
        fader.close()
    }

    @Test fun mediaControllerShowsPausedDuringFadeOutAndCanImmediatelyResume() {
        lateinit var sessionPlayer: QueueSessionPlayer
        val fader = PlaybackVolumeFader(player, handler) { sessionPlayer.refreshQueue() }
        sessionPlayer = QueueSessionPlayer(player, { false }, {}, { false },
            onSetPlayWhenReady = fader::requestPlayWhenReady,
            playWhenReadyOverride = { fader.playWhenReadyOverride })
        val context = RuntimeEnvironment.getApplication()
        val session = MediaSession.Builder(context, sessionPlayer).build()
        try {
            val future = MediaController.Builder(context, session.token).buildAsync()
            advance(0)
            val controller = future.get(3, TimeUnit.SECONDS)
            controller.play()
            advance(400)
            assertTrue(controller.isPlaying)
            controller.pause()
            advance(96)
            assertFalse(controller.playWhenReady)
            assertTrue(player.isPlaying)
            controller.play()
            advance(500)
            assertTrue(controller.isPlaying)
            assertTrue(player.isPlaying)
            assertEquals(1f, player.volume, 0.001f)
            controller.release()
        } finally {
            fader.close()
            session.release()
            sessionPlayer.release()
        }
    }
}
