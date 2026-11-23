package com.bililisten.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bililisten.android.TestFixtures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackSettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val sections = listOf("选择背景音" to "background", "音乐锁屏设置" to "lock_screen")

    @Test fun bothChildrenShareOneDialogAndSystemBackReturnsToSettings() {
        showPlayer()
        openSettings()
        compose.waitForIdle()
        val dialogView = WindowInspector.getGlobalWindowViews().last()
        val dialog = dialogView.findViewTreeOnBackPressedDispatcherOwner() as ComponentDialog
        for ((entry, page) in sections) {
            compose.onNodeWithContentDescription(entry).performScrollTo().performClick()
            compose.onNodeWithTag("settings_page_$page").assertIsDisplayed()
            compose.onNodeWithTag("settings_page_root").assertDoesNotExist()
            compose.onAllNodesWithTag("playback_settings_sheet").assertCountEquals(1)
            assertSame("Navigation must retain the same visible window", dialogView, WindowInspector.getGlobalWindowViews().last())
            compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
            compose.onNodeWithTag("settings_page_$page").assertDoesNotExist()
            assertTrue(dialog.isShowing)
        }
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("playback_settings_sheet").assertDoesNotExist()
    }

    @Test fun rightSwipeReturnsButVolumeSliderAndShortSwipesDoNotNavigate() {
        showPlayer()
        openSettings()
        compose.onNodeWithContentDescription("选择背景音").performScrollTo().performClick()
        compose.onNodeWithContentDescription("背景音音量").performTouchInput {
            swipe(Offset(width * .15f, centerY), Offset(width * .8f, centerY), 350)
        }
        compose.onNodeWithTag("settings_page_background").assertIsDisplayed()
        val volume = compose.onNodeWithContentDescription("背景音音量").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
        ].current
        assertTrue("Dragging the slider must adjust volume", volume > .6f)
        compose.onNodeWithTag("settings_page_background").performTouchInput {
            swipe(Offset(width * .35f, 24f), Offset(width * .47f, 24f), 300)
        }
        compose.onNodeWithTag("settings_page_background").assertIsDisplayed()
        saveImage("settings-background-navigation")
        swipeBack("background")
        compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
        compose.onNodeWithContentDescription("音乐锁屏设置").performScrollTo().performClick()
        saveImage("settings-lock-screen-navigation")
        swipeBack("lock_screen")
        compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
    }

    @Test fun swipingDownFromEitherChildClosesTheWholeFlowAndNextOpenStartsAtRoot() {
        showPlayer()
        openSettings()
        for ((entry, page) in sections) {
            compose.onNodeWithContentDescription(entry).performScrollTo().performClick()
            compose.onNodeWithTag("settings_page_$page").assertIsDisplayed()
            val height = compose.onNodeWithTag("playback_settings_sheet").fetchSemanticsNode().boundsInRoot.height
            compose.onNodeWithTag("settings_sheet_drag_handle", useUnmergedTree = true).performTouchInput {
                swipe(center, Offset(centerX, height * .9f), 350)
            }
            compose.onNodeWithTag("playback_settings_sheet").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("settings_page_root").assertDoesNotExist()
            openSettings()
            compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
            compose.onNodeWithTag("settings_page_$page").assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun upwardSwipesAtTheEndOfSettingsAndBackgroundDoNotMoveTheSheet() {
        showPlayer()
        openSettings()
        val sheet = compose.onNodeWithTag("playback_settings_sheet")
        for ((page, lastText) in listOf("root" to "允许移动网络在线播放", "background" to "列车")) {
            if (page == "background") compose.onNodeWithContentDescription("选择背景音").performScrollTo().performClick()
            compose.onNodeWithText(lastText).performScrollTo()
            val scroll = compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("settings_page_$page")))
            scroll.performTouchInput { swipeUp() }
            val top = sheet.fetchSemanticsNode().boundsInRoot.top
            val lastBottom = compose.onNodeWithText(lastText).fetchSemanticsNode().boundsInRoot.bottom
            repeat(3) {
                scroll.performTouchInput { swipeUp() }
                compose.mainClock.advanceTimeBy(1_000)
                assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
                assertEquals(lastBottom, compose.onNodeWithText(lastText).fetchSemanticsNode().boundsInRoot.bottom, 1f)
            }
            compose.onNodeWithText(lastText).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun backButtonRestoresTheParentScrollPositionOnAShortPhone() {
        showPlayer()
        openSettings()
        val entry = compose.onNodeWithContentDescription("音乐锁屏设置")
        entry.performScrollTo()
        val oldPosition = entry.fetchSemanticsNode().boundsInRoot.top
        entry.performClick()
        compose.onNodeWithContentDescription("返回播放设置").assertIsDisplayed()
        compose.onNodeWithText("预览音乐锁屏").performScrollTo().assertIsDisplayed()
        saveImage("settings-lock-screen-compact")
        compose.onNodeWithContentDescription("返回播放设置").performScrollTo().performClick()
        assertEquals(oldPosition, entry.fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
    }

    private fun swipeBack(page: String) {
        compose.onNodeWithTag("settings_page_$page").performTouchInput {
            swipe(Offset(width * .2f, 24f), Offset(width * .85f, 24f), 350)
        }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("播放设置").performScrollTo().performClick()
    }

    private fun showPlayer() {
        val state = mutableStateOf(ListenUiState(plannedPlayback = TestFixtures.playback()))
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {},
                onBackgroundAudioChanged = { state.value = state.value.copy(playerSettings = state.value.playerSettings.copy(backgroundAudio = it)) },
                onMusicLockScreenChanged = { state.value = state.value.copy(playerSettings = state.value.playerSettings.copy(musicLockScreen = it)) })
        }
    }

    private fun saveImage(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val bitmap = Bitmap.createBitmap(views.first().width, views.first().height, Bitmap.Config.ARGB_8888)
            views.forEach { it.draw(Canvas(bitmap)) }
            File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
