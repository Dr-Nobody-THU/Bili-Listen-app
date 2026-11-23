package com.bililisten.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordCollectionBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val names = listOf("山间来信", "深夜电台", "一段慢时光")
    private val favorites = names.mapIndexed { i, name ->
        val items = (0..3).map { TestFixtures.item(i * 10 + it, collection = "season:${i + 1}").copy(
            title = "$name · 节目 ${it + 1}", collectionTitle = name, orderInCollection = it) }
        FavoriteCollection("season:${i + 1}", TestFixtures.playback(items).let {
            it.copy(plan = it.plan.copy(startPositionMs = (i + 1) * 30_000L))
        }, i.toLong())
    }
    private val state = mutableStateOf(ListenUiState())
    private val selections = mutableListOf<String>()
    private var transportActions = 0

    @Test fun swipingWrapsBothWaysUpdatesNamesAndOnlyConfirmationChangesPlayback() {
        showPlayer()
        enter()
        assertPreview(0)
        repeat(7) { i ->
            swipeNext()
            assertPreview((i + 1) % 3)
            assertOriginalPlayingState()
        }
        // Updated favorites/progress must not reorder the carousel while it is being browsed.
        compose.runOnIdle { state.value = state.value.copy(favorites = favorites.reversed(),
            playbackControl = state.value.playbackControl.copy(positionMs = 48_000)) }
        assertPreview(1)
        swipePrevious()
        assertPreview(0)
        swipePrevious()
        assertPreview(2)
        saveImage("record-browser-portrait")
        compose.onNodeWithTag("record_collection_current").performClick()
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertEquals(listOf("season:3"), selections)
        assertEquals(90_000L, state.value.plannedPlayback!!.plan.startPositionMs)
        assertEquals(0, transportActions)
        compose.onNodeWithTag("listening_forest").assertDoesNotExist()
    }

    @Test fun outsideTapOnPauseCancelsWithoutPressingItAndBackAlsoCancels() {
        showPlayer()
        val pausePosition = compose.onNodeWithContentDescription("播放").fetchSemanticsNode().boundsInRoot.center
        enter()
        swipeNext()
        compose.onRoot().performTouchInput { click(pausePosition) }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertOriginalPlayingState()
        assertEquals(0, transportActions)
        enter()
        swipeNext()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertOriginalPlayingState()
        // Picking the current collection simply closes; it must not reload an older saved position.
        enter()
        compose.onNodeWithTag("record_collection_current").performClick()
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertOriginalPlayingState()
    }

    @Test fun switchDefaultsOffPersistsAndForestNeverOpensCollectionBrowser() {
        val store = PlayerSettingsStore(RuntimeEnvironment.getApplication())
        assertFalse(store.load().recordCollectionSwitch)
        showPlayer(enabled = false)
        compose.onNodeWithTag("vinyl_forest_toggle").performTouchInput { longClick() }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        if (compose.onAllNodesWithTag("listening_forest").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("listening_forest").performClick()
        }
        compose.onNodeWithContentDescription("播放设置").performClick()
        compose.onNodeWithContentDescription("长按唱片切换合集").performScrollTo().assertIsOff().performClick().assertIsOn()
        assertTrue(PlayerSettingsStore(RuntimeEnvironment.getApplication()).load().recordCollectionSwitch)
        val height = compose.onNodeWithTag("playback_settings_sheet").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("settings_sheet_drag_handle", useUnmergedTree = true).performTouchInput {
            swipe(center, Offset(centerX, height * .9f), 350)
        }
        compose.onNodeWithTag("vinyl_forest_toggle").performClick()
        compose.onNodeWithTag("listening_forest").performTouchInput { longClick() }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertOriginalPlayingState()
    }

    @Test fun oneCollectionHasNoDuplicatesAndAnUnfavoritedCurrentCollectionCanCancelSafely() {
        showPlayer(saved = emptyList())
        enter()
        compose.onAllNodesWithTag("record_collection_current").assertCountEquals(1)
        swipeNext()
        assertPreview(0)
        compose.onNodeWithTag("record_collection_current").performClick()
        assertOriginalPlayingState()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w864dp-h393dp-land-xhdpi")
    fun landscapeBrowsesVerticallyInBothDirectionsWithNeighborsAboveAndBelow() {
        showPlayer(skin = AppSkin.MIDNIGHT_BLUE)
        val playPosition = compose.onNodeWithContentDescription("播放").fetchSemanticsNode().boundsInRoot.center
        enter()
        repeat(4) { i ->
            swipeNext(vertical = true)
            assertPreview((i + 1) % 3)
            assertOriginalPlayingState()
        }
        swipePrevious(vertical = true)
        assertPreview(0)
        swipePrevious(vertical = true)
        assertPreview(2)
        val record = compose.onNodeWithTag("record_collection_current").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("record_collection_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(record.right < title.left)
        val adjacent = compose.onAllNodes(hasTestTagPrefix("record_collection_adjacent_")).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.width > 0 && it.height > 0 }
        assertTrue(adjacent.any { it.center.y < record.center.y })
        assertTrue(adjacent.any { it.center.y > record.center.y })
        assertTrue(adjacent.all { kotlin.math.abs(it.center.x - record.center.x) < 2f })
        saveImage("record-browser-landscape-blue")
        assertOriginalPlayingState()
        compose.onRoot().performTouchInput { click(playPosition) }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertOriginalPlayingState()
        enter()
        swipeNext(vertical = true)
        compose.onNodeWithTag("record_collection_current").performTouchInput { click(center) }
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertEquals(listOf("season:2"), selections)
        assertEquals(60_000L, state.value.plannedPlayback!!.plan.startPositionMs)
        assertEquals(0, transportActions)
    }

    private fun showPlayer(enabled: Boolean = true, saved: List<FavoriteCollection> = favorites, skin: AppSkin = AppSkin.MINT_NIGHT) {
        val playback = favorites.first().playback
        state.value = ListenUiState(plannedPlayback = playback, favorites = saved, currentFavoriteId = saved.firstOrNull()?.id,
            playerSettings = PlayerSettings(recordCollectionSwitch = enabled, skin = skin),
            playbackControl = PlaybackControlState(connected = true, mediaId = playback.plan.item.key.asStoreKey(),
                hasActiveItem = true, positionMs = 30_000, durationMs = 240_000))
        compose.setContent {
            ListenScreen(state.value, {}, {}, { transportActions++ }, { transportActions++ }, { transportActions++ },
                { transportActions++ }, { transportActions++ }, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {},
                onRecordCollectionSwitchChanged = {
                    state.value = state.value.copy(playerSettings = PlayerSettingsStore(RuntimeEnvironment.getApplication()).saveRecordCollectionSwitch(it))
                },
                onConfirmRecordCollection = { id ->
                    selections += id
                    val next = favorites.first { it.id == id }.playback
                    state.value = state.value.copy(plannedPlayback = next, currentFavoriteId = id,
                        playbackControl = state.value.playbackControl.copy(mediaId = next.plan.item.key.asStoreKey(), positionMs = next.plan.startPositionMs))
                })
        }
    }

    private fun enter() {
        compose.onNodeWithTag("vinyl_forest_toggle").performTouchInput { longClick() }
        compose.onNodeWithTag("record_collection_browser").assertIsDisplayed()
    }

    private fun swipeNext(vertical: Boolean = false) = compose.onNodeWithTag("record_collection_browser").performTouchInput {
        if (vertical) swipe(Offset(centerX, height * .73f), Offset(centerX, height * .27f), 400)
        else swipe(Offset(width * .73f, centerY), Offset(width * .27f, centerY), 400)
    }

    private fun swipePrevious(vertical: Boolean = false) = compose.onNodeWithTag("record_collection_browser").performTouchInput {
        if (vertical) swipe(Offset(centerX, height * .27f), Offset(centerX, height * .73f), 400)
        else swipe(Offset(width * .27f, centerY), Offset(width * .73f, centerY), 400)
    }

    private fun assertPreview(index: Int) {
        compose.onNodeWithTag("record_collection_current").assertContentDescriptionEquals("${names[index]}，点按播放此合集")
        compose.onNodeWithTag("record_collection_title", useUnmergedTree = true).assertTextEquals(names[index])
    }

    private fun assertOriginalPlayingState() {
        assertTrue(selections.isEmpty())
        assertEquals("season:1", state.value.plannedPlayback!!.queue.collectionId)
        assertEquals(0, transportActions)
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }

    @Test
    @Config(sdk = [35, 36])
    fun holdingCustomAndMaterialButtonsDoesNotPaintALightPressBackground() {
        showPlayer()
        for (label in listOf("播放设置", "播放")) {
            val node = compose.onNodeWithContentDescription(label)
            val before = pixels()
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(300)
            val pressed = pixels()
            assertArrayEquals("Pressing $label must not add a light overlay", before, pressed)
            node.performTouchInput { cancel() }
        }
        assertEquals(0, transportActions)
    }

    @Test
    @Config(sdk = [36])
    fun entryAndExitAnimateWhilePlayingAndProgressKeepsAdvancingWithoutTransportCommands() {
        showPlayer()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(playbackControl = state.value.playbackControl.copy(isPlaying = true)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        val pausePosition = compose.onNodeWithContentDescription("暂停").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("vinyl_forest_toggle").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(160)
        saveImage("record-browser-entering")
        compose.mainClock.advanceTimeBy(400)
        assertPreview(0)
        compose.runOnIdle { state.value = state.value.copy(playbackControl = state.value.playbackControl.copy(positionMs = 33_000)) }
        compose.onRoot().performTouchInput { click(pausePosition) }
        compose.mainClock.advanceTimeBy(160)
        saveImage("record-browser-leaving")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("record_collection_browser").assertDoesNotExist()
        assertTrue(state.value.playbackControl.isPlaying)
        assertEquals(33_000L, state.value.playbackControl.positionMs)
        assertOriginalPlayingState()
    }

    private fun pixels(): IntArray {
        var result = IntArray(0)
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            result = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(result, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
        return result
    }

    private fun saveImage(name: String) {
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
