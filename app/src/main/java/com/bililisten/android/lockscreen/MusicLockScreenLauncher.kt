package com.bililisten.android.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import com.bililisten.android.data.AppGraph

/** Only responds to the user's screen wake during an existing playback service. */
internal class MusicLockScreenLauncher(
    private val context: Context,
    private val playbackRequested: () -> Boolean,
) : AutoCloseable {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            if (!AppGraph.playerSettings(context).load().musicLockScreen || !playbackRequested()) return
            if (!Settings.canDrawOverlays(context)) return
            if (!context.getSystemService(PowerManager::class.java).isInteractive ||
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
            runCatching {
                context.startActivity(Intent(context, MusicLockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }.onFailure { AppGraph.logger(context).error("MusicLockScreen", "System blocked music lock screen.", it) }
        }
    }

    init {
        context.registerLockScreenReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun close() = context.unregisterReceiver(receiver)
}

/** These filters contain only OS-protected SCREEN_ON/OFF and USER_PRESENT broadcasts. */
internal fun Context.registerLockScreenReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    else registerReceiver(receiver, filter)
}
