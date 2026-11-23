package com.bililisten.android.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The app resolves expiring URLs one at a time; expose its logical queue to media controls. */
@UnstableApi
internal class QueueSessionPlayer(
    player: Player,
    private val canSkip: () -> Boolean,
    private val onSkip: (Int) -> Unit,
    private val onResume: () -> Boolean,
    private val onPlayRequest: (Boolean) -> Unit = {},
    private val onSetPlayWhenReady: ((Boolean) -> Unit)? = null,
    private val playWhenReadyOverride: () -> Boolean? = { null },
) : ForwardingSimpleBasePlayer(player) {
    fun refreshQueue() = invalidateState()

    override fun getState(): State {
        val state = super.getState()
        val commands = state.availableCommands.buildUpon()
        val skipCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
        for (command in skipCommands) {
            if (canSkip()) commands.add(command) else commands.remove(command)
        }
        return state.buildUpon().setAvailableCommands(commands.build()).apply {
            playWhenReadyOverride()?.let { setPlayWhenReady(it, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        }.build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> onSkip(1)
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> onSkip(-1)
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        onPlayRequest(playWhenReady)
        if (playWhenReady && onResume()) return Futures.immediateVoidFuture()
        onSetPlayWhenReady?.let {
            it(playWhenReady)
            return Futures.immediateVoidFuture()
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }
}
