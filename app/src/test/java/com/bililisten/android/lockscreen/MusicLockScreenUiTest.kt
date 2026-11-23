package com.bililisten.android.lockscreen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.provider.Settings
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bililisten.android.R
import com.bililisten.android.data.PlayerSettingsStore
import com.bililisten.android.data.AppSkin
import com.bililisten.android.ui.ListenColorScheme
import com.bililisten.android.ui.MusicLockScreenSettingsContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicLockScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun screenShowsPlaybackControlsAndOnlyALongRightSwipeDismissesIt() {
        var dismissed = 0
        var toggled = 0
        var previous = 0
        var next = 0
        val state = mutableStateOf(MusicLockScreenState(title = "把日常听成风景", author = "声音收藏家", collection = "山野之间的声音",
            episode = "3 / 24", time = "22:08", date = "11月13日 星期五", positionMs = 83_000, durationMs = 1_284_000,
            canControl = true, canSkip = true, playRequested = true))
        compose.setContent { MusicLockScreen(state.value, { dismissed++ }, { toggled++ }, { previous++ }, { next++ }) }
        compose.onNodeWithText("栖声").assertIsDisplayed()
        compose.onNodeWithText("01:23").assertIsDisplayed()
        compose.onNodeWithText("21:24").assertIsDisplayed()
        compose.onNodeWithContentDescription("暂停").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("上一条").performClick()
        compose.onNodeWithContentDescription("下一条").performClick()
        compose.runOnIdle { assertEquals(1, toggled); assertEquals(1, previous); assertEquals(1, next) }
        saveImage("music-lock-screen")
        compose.runOnIdle { state.value = state.value.copy(skin = AppSkin.MIDNIGHT_BLUE, playRequested = false) }
        compose.onNodeWithContentDescription("播放").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, toggled) }
        saveImage("music-lock-screen-blue")
        renderIcon()
        compose.onNodeWithTag("music_lock_screen").performTouchInput {
            swipe(Offset(width * .35f, height * .4f), Offset(width * .47f, height * .4f), 250)
        }
        compose.runOnIdle { assertEquals(0, dismissed) }
        compose.onNodeWithTag("music_lock_screen").performTouchInput {
            swipe(Offset(width * .2f, height * .4f), Offset(width * .85f, height * .4f), 300)
        }
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun shortPhoneKeepsLongTitlesControlsAndSystemLockscreenExitVisible() {
        compose.setContent {
            MusicLockScreen(MusicLockScreenState(title = "一段很长的节目名称：在城市边缘寻找安静的日常与声音", author = "声音收藏家",
                collection = "周末漫步", episode = "9 / 32", time = "22:08", date = "11月13日 星期五",
                positionMs = 83_000, durationMs = 1_284_000, canControl = true, canSkip = true), {}, {}, {}, {})
        }
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回系统锁屏").assertIsDisplayed()
        val record = compose.onNodeWithContentDescription("黑胶唱片暂停，唱针已抬起").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("一段很长的节目名称：在城市边缘寻找安静的日常与声音").fetchSemanticsNode().boundsInRoot
        assertTrue(record.height > 0)
        assertTrue(record.bottom <= title.top)
        saveImage("music-lock-screen-compact")
    }

    @Test fun enablingRequiresPermissionAndPreviewDoesNotEnableAnything() {
        val store = PlayerSettingsStore(compose.activity)
        val enabled = mutableStateOf(store.load().musicLockScreen)
        ShadowSettings.setCanDrawOverlays(false)
        compose.setContent { MaterialTheme(colorScheme = ListenColorScheme) {
            MusicLockScreenSettingsContent(enabled.value, { enabled.value = store.saveMusicLockScreen(it).musicLockScreen }, {})
        } }
        compose.onNodeWithContentDescription("启用音乐锁屏").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertFalse(store.load().musicLockScreen) }
        val intent = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${compose.activity.packageName}", intent.dataString)
        compose.runOnIdle {
            ShadowSettings.setCanDrawOverlays(true)
            shadowOf(compose.activity).receiveResult(intent, Activity.RESULT_OK, Intent())
        }
        compose.onNodeWithContentDescription("启用音乐锁屏").assertIsOn()
        compose.runOnIdle { assertTrue(PlayerSettingsStore(compose.activity).load().musicLockScreen) }
        saveImage("music-lock-screen-settings")
        compose.onNodeWithContentDescription("启用音乐锁屏").performClick().assertIsOff()
        compose.onNodeWithText("预览音乐锁屏").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(store.load().musicLockScreen)
            val preview = shadowOf(compose.activity).nextStartedActivity
            assertEquals(MusicLockScreenActivity::class.java.name, preview.component!!.className)
            assertTrue(preview.getBooleanExtra(MusicLockScreenActivity.EXTRA_PREVIEW, false))
        }
    }

    private fun saveImage(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val view = views.first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            views.forEach { it.draw(Canvas(bitmap)) }
            save(bitmap, name)
        }
    }

    private fun renderIcon() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("栖声", context.getString(R.string.app_name))
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        for (size in listOf(48, 192)) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            save(bitmap, "qisheng-icon-$size")
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
