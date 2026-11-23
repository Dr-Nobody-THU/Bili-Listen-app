package com.bililisten.android.player

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.Player

/** Changes only this player's gain. Audio focus and system volume remain owned by ExoPlayer. */
internal class PlaybackVolumeFader(
    private val player: Player,
    private val handler: Handler,
    private val onRequestChanged: () -> Unit = {},
) {
    private var enabled = true
    private var targetVolume: Float? = null
    private var startVolume = 1f
    private var startedAt = 0L
    private var waitingForAudio = false

    // During fade-out ExoPlayer is still audible, but controls must already offer "play".
    var playWhenReadyOverride: Boolean? = null
        private set
    val playRequested: Boolean get() = playWhenReadyOverride ?: player.playWhenReady

    private val frame = object : Runnable {
        override fun run() {
            val target = targetVolume ?: return
            val fraction = ((SystemClock.uptimeMillis() - startedAt).toFloat() / DURATION_MS).coerceIn(0f, 1f)
            val smooth = fraction * fraction * (3f - 2f * fraction)
            player.volume = startVolume + (target - startVolume) * smooth
            if (fraction >= 1f) {
                if (target == 0f) pauseImmediately() else {
                    cancelAnimation()
                    playWhenReadyOverride = null
                    onRequestChanged()
                }
            } else handler.postDelayed(this, FRAME_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player.isPlaying) {
                if (waitingForAudio && playRequested) startRamp(1f)
            } else if (targetVolume == 0f) {
                // A buffering/focus interruption must not revive a pending pause.
                pauseImmediately()
            } else if (targetVolume == 1f && player.playWhenReady) {
                cancelAnimation()
                waitingForAudio = true
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!player.playWhenReady) resetAnimation()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                resetAnimation()
            }
        }
    }

    init { player.addListener(listener) }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        val requested = playRequested
        enabled = value
        if (!value) {
            cancelAnimation()
            playWhenReadyOverride = null
            player.playWhenReady = requested
            player.volume = 1f
            onRequestChanged()
        }
    }

    fun requestPlayWhenReady(play: Boolean) {
        cancelAnimation()
        if (!enabled) {
            playWhenReadyOverride = null
            player.playWhenReady = play
            player.volume = 1f
        } else if (play) {
            playWhenReadyOverride = true
            if (!player.isPlaying) player.volume = 0f
            waitingForAudio = true
            player.play()
            // A ready player can notify synchronously from play(); do not restart that ramp.
            if (player.isPlaying && waitingForAudio) startRamp(1f)
        } else {
            playWhenReadyOverride = false
            if (player.isPlaying && player.volume > 0f) startRamp(0f) else pauseImmediately()
        }
        onRequestChanged()
    }

    /** Track changes, failures and network stops must cancel any pending delayed pause. */
    fun pauseImmediately() {
        cancelAnimation()
        playWhenReadyOverride = null
        player.pause()
        player.volume = 1f
        onRequestChanged()
    }

    fun close() {
        cancelAnimation()
        player.removeListener(listener)
    }

    private fun startRamp(target: Float) {
        cancelAnimation()
        startVolume = player.volume
        targetVolume = target
        startedAt = SystemClock.uptimeMillis()
        handler.post(frame)
    }

    private fun cancelAnimation() {
        handler.removeCallbacks(frame)
        targetVolume = null
        waitingForAudio = false
    }

    private fun resetAnimation() {
        cancelAnimation()
        playWhenReadyOverride = null
        player.volume = 1f
        onRequestChanged()
    }

    companion object {
        private const val DURATION_MS = 400L
        private const val FRAME_MS = 16L
    }
}
