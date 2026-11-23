package com.bililisten.android.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.PlayerSettingsStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicLockScreenTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetPlaybackSettings() {
        AppGraph.playerSettings(context).saveMusicLockScreen(false)
        AppGraph.repository(context).activePlayback = null
        ShadowSettings.setCanDrawOverlays(false)
    }

    @After fun clearPlaybackSettings() {
        AppGraph.playerSettings(context).saveMusicLockScreen(false)
        AppGraph.repository(context).activePlayback = null
    }

    @Test fun automaticPageRequiresOptInPermissionPlaybackAndALockedInteractiveScreen() {
        val settings = AppGraph.playerSettings(context)
        val keyguard = shadowOf(context.getSystemService(KeyguardManager::class.java))
        val power = shadowOf(context.getSystemService(PowerManager::class.java))
        keyguard.setKeyguardLocked(true)
        power.setIsInteractive(true)
        var playing = true
        val launcher = MusicLockScreenLauncher(context) { playing }
        fun wake(): Intent? {
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
            shadowOf(Looper.getMainLooper()).idle()
            return shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        }
        try {
            ShadowSettings.setCanDrawOverlays(true)
            assertFalse(settings.load().musicLockScreen)
            assertNull(wake())
            settings.saveMusicLockScreen(true)
            assertTrue(PlayerSettingsStore(context).load().musicLockScreen)
            ShadowSettings.setCanDrawOverlays(false)
            assertNull(wake())
            ShadowSettings.setCanDrawOverlays(true)
            playing = false
            assertNull(wake())
            playing = true
            keyguard.setKeyguardLocked(false)
            assertNull(wake())
            keyguard.setKeyguardLocked(true)
            power.setIsInteractive(false)
            assertNull(wake())
            assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
            power.setIsInteractive(true)
            val intent = wake()!!
            assertEquals(MusicLockScreenActivity::class.java.name, intent.component!!.className)
            assertFalse(intent.getBooleanExtra(MusicLockScreenActivity.EXTRA_PREVIEW, false))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        } finally { launcher.close() }
        assertNull(wake())
    }

    @Test fun closingMusicPageKeepsSystemKeyguardAndPlaybackIntact() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        shadowOf(keyguard).setIsKeyguardSecure(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val playback = TestFixtures.playback()
        val repository = AppGraph.repository(context)
        repository.activePlayback = playback
        AppGraph.playerSettings(context).saveMusicLockScreen(true)
        val controller = Robolectric.buildActivity(MusicLockScreenActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(shadowOf(activity).showWhenLocked)
            assertFalse(shadowOf(activity).turnScreenOn)
            assertEquals(0, activity.window.attributes.flags and (WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON))
            activity.dismiss()
            assertTrue(activity.isFinishing)
            assertTrue(keyguard.isKeyguardLocked)
            assertTrue(keyguard.isKeyguardSecure)
            assertEquals(playback, repository.activePlayback)
        } finally { controller.pause().stop().destroy(); repository.activePlayback = null }
    }

    @Test fun unlockedPhoneRejectsAutomaticPageButAllowsPreviewWithoutEnablingIt() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        val automatic = Robolectric.buildActivity(MusicLockScreenActivity::class.java).create()
        assertTrue(automatic.get().isFinishing)
        automatic.destroy()
        val preview = Robolectric.buildActivity(MusicLockScreenActivity::class.java,
            Intent(context, MusicLockScreenActivity::class.java).putExtra(MusicLockScreenActivity.EXTRA_PREVIEW, true)).setup()
        try {
            assertFalse(preview.get().isFinishing)
            assertFalse(shadowOf(preview.get()).showWhenLocked)
            assertFalse(shadowOf(preview.get()).turnScreenOn)
            assertFalse(AppGraph.playerSettings(context).load().musicLockScreen)
        } finally { preview.pause().stop().destroy() }
    }

    @Test fun screenOffAndSuccessfulSystemUnlockDismissTheMusicPage() {
        val repository = AppGraph.repository(context)
        repository.activePlayback = TestFixtures.playback()
        AppGraph.playerSettings(context).saveMusicLockScreen(true)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        for (action in listOf(Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT)) {
            val activity = Robolectric.buildActivity(MusicLockScreenActivity::class.java).setup()
            context.sendBroadcast(Intent(action))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(activity.get().isFinishing)
            assertNotNull(repository.activePlayback)
            activity.pause().stop().destroy()
        }
        repository.activePlayback = null
    }
}
