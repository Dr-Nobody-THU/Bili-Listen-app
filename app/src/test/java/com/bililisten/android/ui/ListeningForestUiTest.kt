package com.bililisten.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.*
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
class ListeningForestUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordTogglesSharedForestAndCollectionSwitchesPreserveItsTotalWithoutChangingPlayback() {
        val state = mutableStateOf(playerState(60L * 60 * 60 * 1000))
        var playActions = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ListenScreen(state.value, {}, {}, { playActions++ }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
        }
        compose.onNodeWithTag("vinyl_forest_toggle").performClick()
        compose.onNodeWithTag("listening_forest").assertIsDisplayed()
        compose.onNodeWithText("小树 2").assertIsDisplayed()
        compose.onNodeWithText("总收听 60小时").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        saveImage("player-listening-forest")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("listening_forest").assertIsDisplayed()
        compose.runOnIdle {
            val other = TestFixtures.playback((0..2).map { TestFixtures.item(it, collection = "season:456") })
            state.value = state.value.copy(plannedPlayback = other)
        }
        compose.onNodeWithText("小树 2").assertIsDisplayed()
        compose.onNodeWithText("总收听 60小时").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(totalListeningTimeMs = 5 * TREE_LISTENING_MS) }
        compose.onNodeWithText("小树 1 · 中树 1").assertIsDisplayed()
        compose.onNodeWithText("总收听 120小时").assertIsDisplayed()
        compose.onNodeWithTag("listening_forest").performClick()
        compose.onNodeWithTag("vinyl_forest_toggle").assertIsDisplayed()
        compose.onNodeWithTag("listening_forest").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, playActions)
            assertEquals(23_000L, state.value.playbackControl.positionMs)
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h440dp-xhdpi")
    fun fourSeasonsKeepTheSameTreesAndShowTheirDistinctLandscapes() {
        val season = mutableStateOf(ForestSeason.SPRING)
        val total = 63 * TREE_LISTENING_MS + TREE_LISTENING_MS / 2
        compose.setContent {
            MaterialTheme(colorScheme = listenColorScheme(AppSkin.MIDNIGHT_BLUE)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
                    ListeningForest(total, season.value, {})
                }
            }
        }
        for (value in ForestSeason.entries) {
            compose.runOnIdle { season.value = value }
            compose.onNodeWithText(value.label).assertIsDisplayed()
            compose.onNodeWithText("小树 3 · 中树 3 · 大树 3").assertIsDisplayed()
            compose.onNodeWithText("距下一株 12小时").assertIsDisplayed()
            saveImage("forest-${value.name.lowercase()}")
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun compactPhoneKeepsForestAndTransportControlsVisible() {
        val state = playerState(TREE_LISTENING_MS / 2)
        compose.setContent {
            ListenScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
        }
        compose.onNodeWithTag("vinyl_forest_toggle").performClick()
        compose.onNodeWithText("总收听 12小时").assertIsDisplayed()
        for (name in listOf("播放", "上一条", "下一条", "播放设置", "播放队列")) {
            compose.onNodeWithContentDescription(name).assertIsDisplayed()
        }
        val canvas = compose.onNodeWithTag("forest_canvas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val art = compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot
        assertTrue(canvas.height > 0)
        assertTrue(canvas.top >= art.top && canvas.bottom <= art.bottom)
        saveImage("forest-player-compact")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h440dp-xhdpi")
    fun visibleForestAnimatesAndSeasonalFramesCanBeExported() {
        compose.mainClock.autoAdvance = false
        val season = mutableStateOf(ForestSeason.AUTUMN)
        compose.setContent {
            MaterialTheme(colorScheme = listenColorScheme(AppSkin.MIDNIGHT_BLUE)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
                    ListeningForest(63 * TREE_LISTENING_MS + TREE_LISTENING_MS / 2, season.value, {})
                }
            }
        }
        compose.runOnIdle { WindowInspector.getGlobalWindowViews().first().dispatchWindowFocusChanged(true) }
        compose.mainClock.advanceTimeBy(100)
        ForestAnimationPreview(File("build/reports/ui/forest-animated-autumn.gif")).use { preview ->
            var firstPixels: IntArray? = null
            repeat(64) { index ->
                compose.mainClock.advanceTimeBy(80)
                compose.waitForIdle()
                compose.runOnIdle {
                    val bitmap = captureScreen()
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    if (index == 0) firstPixels = pixels
                    if (index == 16) assertTrue("Tree crowns and particles should move between rendered frames",
                        pixels.indices.count { pixels[it] != firstPixels!![it] } > 200)
                    preview.frame(bitmap)
                    bitmap.recycle()
                }
            }
        }
        for (value in listOf(ForestSeason.SPRING, ForestSeason.SUMMER, ForestSeason.WINTER)) {
            compose.runOnIdle { season.value = value }
            compose.mainClock.advanceTimeBy(1_000)
            saveImage("forest-moving-${value.name.lowercase()}")
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h440dp-xhdpi")
    fun forestsOfEverySizeStayOnOneCanvasWithoutPaging() {
        var returns = 0
        val total = mutableStateOf(4 * 16 * TREE_LISTENING_MS)
        compose.setContent {
            MaterialTheme(colorScheme = ListenColorScheme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
                    ListeningForest(total.value, ForestSeason.WINTER, { returns++ })
                }
            }
        }
        for (large in listOf(4L, 40L, 1_000L)) {
            compose.runOnIdle { total.value = large * 16 * TREE_LISTENING_MS }
            compose.onNodeWithText("大树 $large").assertIsDisplayed()
            compose.onNodeWithContentDescription("上一片林地").assertDoesNotExist()
            compose.onNodeWithContentDescription("下一片林地").assertDoesNotExist()
            compose.onNodeWithTag("forest_canvas", useUnmergedTree = true).assertIsDisplayed()
            saveImage("forest-shared-$large")
        }
        compose.runOnIdle { total.value = Long.MAX_VALUE }
        compose.onNodeWithTag("forest_canvas", useUnmergedTree = true).assertIsDisplayed()
        saveImage("forest-shared-distant")
        compose.runOnIdle { assertEquals(0, returns) }
        compose.onNodeWithTag("listening_forest").performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }

    private fun playerState(total: Long): ListenUiState {
        val playback = TestFixtures.playback()
        return ListenUiState(plannedPlayback = playback, totalListeningTimeMs = total,
            playbackControl = PlaybackControlState(connected = true, mediaId = playback.plan.item.key.asStoreKey(),
                positionMs = 23_000, durationMs = 240_000, hasActiveItem = true, localAudio = true))
    }

    private fun saveImage(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = captureScreen()
            File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun captureScreen(): Bitmap {
        val views = WindowInspector.getGlobalWindowViews()
        return Bitmap.createBitmap(views.first().width, views.first().height, Bitmap.Config.ARGB_8888).also { bitmap ->
            views.forEach { it.draw(Canvas(bitmap)) }
        }
    }
}
