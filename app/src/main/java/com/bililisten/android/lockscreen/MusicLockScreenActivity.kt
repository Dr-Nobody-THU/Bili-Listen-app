package com.bililisten.android.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bililisten.android.data.AppGraph
import com.bililisten.android.player.AudioPlaybackService
import com.bililisten.android.widget.PlaybackWidgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicLockScreenActivity : ComponentActivity() {
    private val repository by lazy { AppGraph.repository(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var screen by mutableStateOf(MusicLockScreenState())
    private var preview = false
    private var receiverRegistered = false
    private var visible = false

    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!preview && (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_OFF)) dismiss()
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (!visible || isFinishing) return
            updateScreen()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        setShowWhenLocked(!preview)
        setTurnScreenOn(false)
        if (!preview && !mayRemainVisible()) { dismiss(); return }
        enableEdgeToEdge()
        // Never requestDismissKeyguard, alter a system wallpaper, or keep/wake the screen on.
        setContent { MusicLockScreen(screen, onDismiss = ::dismiss,
            onToggle = { sendPlaybackAction(AudioPlaybackService.ACTION_WIDGET_TOGGLE) },
            onPrevious = { sendPlaybackAction(AudioPlaybackService.ACTION_WIDGET_PREVIOUS) },
            onNext = { sendPlaybackAction(AudioPlaybackService.ACTION_WIDGET_NEXT) }) }
        registerLockScreenReceiver(exitReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        receiverRegistered = true
    }

    override fun onStart() {
        super.onStart()
        visible = true
        handler.post(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        setShowWhenLocked(!preview)
        updateScreen()
    }

    override fun onStop() {
        visible = false
        handler.removeCallbacks(ticker)
        super.onStop()
        if (!isChangingConfigurations) dismiss()
    }

    private fun mayRemainVisible(): Boolean = AppGraph.playerSettings(this).load().musicLockScreen &&
        getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        getSystemService(PowerManager::class.java).isInteractive && repository.activePlayback != null

    private fun updateScreen() {
        if (!preview && !mayRemainVisible()) { dismiss(); return }
        val active = repository.activePlayback
        val playback = active ?: if (preview) PlaybackWidgets.restoredPlayback(this) else null
        val snapshot = PlaybackWidgets.state(this).takeIf {
            active != null && it.playback?.plan?.item?.key == active.plan.item.key && it.playback.queue.collectionId == active.queue.collectionId
        }
        val now = Date()
        screen = MusicLockScreenState(
            title = playback?.plan?.item?.title ?: "让声音慢下来",
            author = playback?.plan?.item?.authorName ?: "栖声",
            collection = playback?.queue?.collectionTitle.orEmpty(),
            episode = playback?.let { "${it.plan.startIndex + 1} / ${it.queue.items.size}" }.orEmpty(),
            coverUrl = playback?.plan?.item?.coverUrl,
            positionMs = snapshot?.positionMs ?: playback?.plan?.startPositionMs ?: 0,
            durationMs = snapshot?.durationMs ?: playback?.plan?.item?.durationMs ?: 0,
            playing = visible && snapshot?.playing == true,
            playRequested = snapshot?.playRequested == true,
            loading = repository.playbackLoading || snapshot?.loading == true,
            canControl = active != null,
            canSkip = (playback?.queue?.items?.size ?: 0) > 1 && active != null,
            error = repository.playbackError,
            time = DateFormat.getTimeFormat(this).format(now),
            date = SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(now),
            preview = preview,
            skin = AppGraph.playerSettings(this).load().skin,
        )
    }

    private fun sendPlaybackAction(action: String) {
        if (repository.activePlayback != null) startService(Intent(this, AudioPlaybackService::class.java).setAction(action))
    }

    internal fun dismiss() {
        if (!isFinishing) finishAndRemoveTask()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(exitReceiver)
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_PREVIEW = "preview"
        internal fun preview(context: Context) {
            context.startActivity(Intent(context, MusicLockScreenActivity::class.java)
                .putExtra(EXTRA_PREVIEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}
