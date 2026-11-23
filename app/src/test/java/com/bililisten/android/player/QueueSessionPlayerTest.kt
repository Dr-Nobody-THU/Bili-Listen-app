package com.bililisten.android.player

import android.media.session.PlaybackState
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowMediaSession
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 36], shadows = [QueueSessionPlayerTest.CapturingPlatformSession::class])
class QueueSessionPlayerTest {
    // Robolectric's platform MediaController returns a manually seeded value, not the
    // state of its paired session. Capture the actual platform publication instead.
    @Implements(android.media.session.MediaSession::class)
    class CapturingPlatformSession : ShadowMediaSession() {
        @Implementation fun setPlaybackState(state: PlaybackState?) { lastState = state }
        companion object { var lastState: PlaybackState? = null }
    }
    private class OneItemPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA, Player.COMMAND_PLAY_PAUSE, Player.COMMAND_RELEASE,
            ).build())
            .setPlaylist(listOf(MediaItemData.Builder("one").setMediaItem(MediaItem.fromUri("file:///one.m4a")).build()))
            .setCurrentMediaItemIndex(0).setPlaybackState(Player.STATE_READY).build()
    }

    @Test fun nextAndPreviousCommandsReachLogicalQueueWithOnlyOneResolvedMediaItem() {
        val skips = mutableListOf<Int>()
        val player = QueueSessionPlayer(OneItemPlayer(), { true }, { skips += it }, { false })
        player.seekToNext()
        player.seekToNextMediaItem()
        player.seekToPrevious()
        player.seekToPreviousMediaItem()
        assertEquals(listOf(1, 1, -1, -1), skips)
        player.release()
    }

    @Test fun changesInQueueSizeNotifyControllersAndDisableSingleItemSkipping() {
        var multiple = true
        var changes = 0
        var skipped = false
        val player = QueueSessionPlayer(OneItemPlayer(), { multiple }, { skipped = true }, { false })
        player.addListener(object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { changes++ }
        })
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        multiple = false
        player.refreshQueue()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        player.seekToNext()
        assertFalse(skipped)
        assertTrue(changes > 0)
        player.release()
    }

    @Test fun platformMediaSessionAdvertisesAndDispatchesNextAndPrevious() {
        val skips = mutableListOf<Int>()
        val player = QueueSessionPlayer(OneItemPlayer(), { true }, { skips += it }, { false })
        val context = RuntimeEnvironment.getApplication()
        CapturingPlatformSession.lastState = null
        val session = MediaSession.Builder(context, player).build()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val actions = CapturingPlatformSession.lastState!!.actions
            assertTrue(actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            assertTrue(actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
            val connection = MediaController.Builder(context, session.token).buildAsync()
            shadowOf(Looper.getMainLooper()).idle()
            val controller = connection.get(3, TimeUnit.SECONDS)
            assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            controller.seekToNext()
            controller.seekToPrevious()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(1, -1), skips)
            controller.release()
        } finally {
            session.release()
            player.release()
        }
    }
}
