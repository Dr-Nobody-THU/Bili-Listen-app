package com.bililisten.android.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.*
import com.bililisten.android.lockscreen.MusicLockScreen
import com.bililisten.android.lockscreen.MusicLockScreenState
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
@Config(sdk = [35], qualifiers = "zh-rCN-w864dp-h393dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun prepared(skin: AppSkin = AppSkin.MINT_NIGHT): ListenUiState {
        val playback = TestFixtures.playback(index = 2)
        return ListenUiState(plannedPlayback = playback, playerSettings = PlayerSettings(skin = skin),
            playbackControl = PlaybackControlState(connected = true, mediaId = playback.plan.item.key.asStoreKey(),
                hasActiveItem = true, positionMs = 63_000, durationMs = 240_000, localAudio = true),
            totalListeningTimeMs = 480 * 3_600_000L)
    }

    @Test fun renoProportionsShowTheRecordBesideFullyVisibleControlsAndWorkingActions() {
        val state = prepared()
        val actions = mutableListOf<String>()
        compose.setContent {
            // Leave room for system bars and a side cutout, in addition to the screen's own insets.
            Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                TestPlayer(state, actions)
            }
        }
        assertLandscapePlayer(state)
        listOf("上一条", "播放", "下一条", "前进 15 秒", "后退 15 秒").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        assertEquals(listOf("previous", "toggle", "next", "seek:15000", "seek:-15000"), actions)
        saveImage("landscape-player-mint")
        compose.onNodeWithTag("vinyl_forest_toggle").performClick()
        compose.onNodeWithTag("listening_forest").assertIsDisplayed()
        val forest = compose.onNodeWithTag("listening_forest").fetchSemanticsNode().boundsInRoot
        assertInside(forest, compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot)
        saveImage("landscape-forest")
        compose.onNodeWithTag("listening_forest").performClick()
        compose.onNodeWithContentDescription("播放队列").performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        val options = compose.onNodeWithTag("sheet_options").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("playback_queue").fetchSemanticsNode().boundsInRoot
        assertTrue(options.right < list.left)
        assertTrue("The queue should use the sheet height", list.height > 400f)
        compose.onNodeWithTag("queue_item_2").assertIsDisplayed()
        saveImage("landscape-queue")
    }

    @Test @Config(qualifiers = "zh-rCN-w740dp-h360dp-land-xhdpi")
    fun shorterLandscapeWindowStillFitsTheHeaderTitleAndAllTransportButtons() {
        val state = prepared(AppSkin.MIDNIGHT_BLUE)
        compose.setContent {
            Box(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 24.dp)) { TestPlayer(state) }
        }
        assertLandscapePlayer(state)
        saveImage("landscape-player-blue-compact")
    }

    @Test @Config(qualifiers = "zh-rCN-w740dp-h360dp-land-xhdpi")
    fun rightColumnHeaderCanImportAndDismissEditingWithoutActivatingThePlayer() {
        val state = mutableStateOf(prepared())
        val imports = mutableListOf<ResumePolicy>()
        var playbackActions = 0
        compose.setContent {
            ListenScreen(state.value, { state.value = state.value.copy(input = it) }, { imports += it },
                { playbackActions++ }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
        }
        val headerParts = listOf("player_header", "player_import_input", "player_header_brand", "player_header_action")
        val closedBounds = headerParts.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        fun assertHeaderStable() {
            closedBounds.forEach { (tag, expected) ->
                val actual = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals("$tag left", expected.left, actual.left, 1f)
                assertEquals("$tag top", expected.top, actual.top, 1f)
                assertEquals("$tag width", expected.width, actual.width, 1f)
                assertEquals("$tag height", expected.height, actual.height, 1f)
            }
        }
        compose.onNodeWithContentDescription("展开输入栏").performTouchInput { click() }
        assertHeaderStable()
        compose.onNode(hasSetTextAction()).performTextInput("BV1testLandscape")
        assertHeaderStable()
        saveImage("landscape-import-expanded")
        compose.runOnIdle { state.value = state.value.copy(error = "链接格式不正确") }
        compose.onNodeWithText("链接格式不正确").assertIsDisplayed()
        assertHeaderStable()
        compose.runOnIdle { state.value = state.value.copy(error = null) }
        val header = compose.onNodeWithTag("player_header").fetchSemanticsNode().boundsInRoot
        assertInside(compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot, header)
        assertInside(compose.onNodeWithText("确定").fetchSemanticsNode().boundsInRoot, header)
        compose.onNodeWithText("确定").performTouchInput { click() }
        assertEquals(listOf(ResumePolicy.PREFER_REQUESTED_ITEM), imports)
        assertEquals("BV1testLandscape", state.value.input)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertHeaderStable()

        compose.onNodeWithContentDescription("展开输入栏").performTouchInput { click() }
        compose.onNodeWithTag("player_artwork_area").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("listening_forest").assertDoesNotExist()
        compose.onNodeWithTag("vinyl_forest_toggle").performTouchInput { click() }
        compose.onNodeWithTag("listening_forest").assertIsDisplayed()
        assertEquals(0, playbackActions)

        // A new installation with no selected program must retain the same usable import row.
        compose.runOnIdle { state.value = ListenUiState() }
        assertHeaderBesideArtwork()
        compose.onNodeWithContentDescription("展开输入栏").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTextInput("BV1firstImport")
        compose.onNodeWithText("确定").performTouchInput { click() }
        assertEquals(2, imports.size)
    }

    @Test fun landscapeBackgroundSettingsKeepTheSoundListBesideTheVolumeAndCanReturn() {
        val state = mutableStateOf(prepared(AppSkin.MIDNIGHT_BLUE))
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {
                    state.value = state.value.copy(playerSettings = state.value.playerSettings.copy(backgroundAudio = it))
                })
        }
        compose.onNodeWithContentDescription("播放设置").performClick()
        compose.onNodeWithContentDescription("选择背景音").performScrollTo().performClick()
        val options = compose.onNodeWithTag("sheet_options").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("sheet_content").fetchSemanticsNode().boundsInRoot
        assertTrue(options.right < content.left)
        compose.onNodeWithText(BackgroundSound.entries.last().label).performScrollTo().performClick()
        assertTrue(state.value.playerSettings.backgroundAudio.enabled)
        assertEquals(BackgroundSound.entries.last(), state.value.playerSettings.backgroundAudio.sound)
        compose.onNodeWithContentDescription("背景音音量").performScrollTo().assertIsDisplayed()
        saveImage("landscape-background-sounds")
        compose.onNodeWithContentDescription("返回播放设置").performScrollTo().performClick()
        compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
        compose.onNodeWithText("播放设置").performScrollTo()
        saveImage("landscape-playback-settings")
        compose.onNodeWithContentDescription("音乐锁屏设置").performScrollTo().performClick()
        compose.onNodeWithTag("settings_page_lock_screen").assertIsDisplayed()
        compose.onNodeWithText("预览音乐锁屏").performScrollTo().assertIsDisplayed()
        saveImage("landscape-lock-screen-settings")
        compose.onNodeWithContentDescription("返回播放设置").performScrollTo().performClick()
        compose.onNodeWithTag("settings_page_root").assertIsDisplayed()
    }

    @Test fun manualOrderKeepsDoneAtTheTopRightAndAllowsDraggingInLandscape() {
        val repository = ListenRepository(RuntimeEnvironment.getApplication())
        val items = (0 until 10).map { TestFixtures.item(it) }
        items.indices.forEach { repository.toggleSpecialFavorite(TestFixtures.playback(items, it)) }
        val playback = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        val state = prepared().copy(plannedPlayback = playback, currentCollectionPlaybackOrder = CollectionPlaybackOrder.FAVORITED_ASC,
            specialFavoriteManualItems = repository.specialFavoriteManualItems())
        var saved: List<ContentKey>? = null
        compose.setContent {
            ListenScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {}, onSaveSpecialFavoriteOrder = { saved = it })
        }
        compose.onNodeWithContentDescription("播放队列").performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_manual").performClick()
        val done = compose.onNodeWithText("完成").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("special_order_list").fetchSemanticsNode().boundsInRoot
        assertTrue(done.center.x > list.center.x && done.bottom < list.top)
        val handle = compose.onNodeWithTag("special_order_handle_${items[0].key.asStoreKey()}")
        val target = compose.onNodeWithTag("special_order_item_${items[1].key.asStoreKey()}")
        val distance = target.fetchSemanticsNode().boundsInRoot.center.y - handle.fetchSemanticsNode().boundsInRoot.center.y
        handle.performTouchInput {
            down(center); advanceEventTime(600)
            repeat(10) { moveBy(Offset(0f, distance / 10), delayMillis = 32) }
            up()
        }
        saveImage("landscape-manual-order")
        compose.onNodeWithText("完成").performClick()
        assertEquals(listOf(items[1].key, items[0].key), saved!!.take(2))
    }

    @Test fun artworkChoiceAndPlaybackStateSurviveOrientationAndSavedStateRestoration() {
        val state = prepared()
        val actions = mutableListOf<String>()
        val configuration = mutableStateOf(Configuration(RuntimeEnvironment.getApplication().resources.configuration))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CompositionLocalProvider(LocalConfiguration provides configuration.value) { TestPlayer(state, actions) } }
        compose.onNodeWithTag("vinyl_forest_toggle").performClick()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT; screenWidthDp = 393; screenHeightDp = 864
            }
        }
        compose.onNodeWithTag("landscape_player").assertDoesNotExist()
        compose.onNodeWithTag("listening_forest").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE; screenWidthDp = 864; screenHeightDp = 393
            }
        }
        compose.onNodeWithTag("landscape_player").assertIsDisplayed()
        compose.onNodeWithTag("listening_forest").assertIsDisplayed()
        compose.onNodeWithText("01:03").assertIsDisplayed()
        assertTrue("Rotation must not dispatch playback/seek actions", actions.isEmpty())
    }

    @Test fun landscapeMusicLockScreenFitsClockArtworkProgressAndControls() {
        var toggle = 0
        var next = 0
        var previous = 0
        var dismiss = 0
        compose.setContent {
            MusicLockScreen(MusicLockScreenState(title = "山野之间的声音：听一段雨后的森林", author = "声音收藏家",
                collection = "把日常听成风景", episode = "3 / 8", time = "21:36", date = "11月23日 · 星期一",
                positionMs = 63_000, durationMs = 240_000, canControl = true, canSkip = true,
                skin = AppSkin.MIDNIGHT_BLUE), { dismiss++ }, { toggle++ }, { previous++ }, { next++ })
        }
        val art = compose.onNodeWithTag("lockscreen_artwork_area").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("lockscreen_controls").fetchSemanticsNode().boundsInRoot
        assertTrue(art.right < controls.left)
        val screen = compose.onNodeWithTag("landscape_lock_screen").fetchSemanticsNode().boundsInRoot
        listOf("上一条", "播放", "下一条", "节目播放进度", "返回系统锁屏").forEach {
            val node = compose.onNodeWithContentDescription(it).assertIsDisplayed()
            assertInside(node.fetchSemanticsNode().boundsInRoot, screen)
        }
        saveImage("landscape-lock-screen")
        listOf("上一条", "播放", "下一条").forEach { compose.onNodeWithContentDescription(it).performClick() }
        assertEquals(listOf(1, 1, 1), listOf(previous, toggle, next))
        compose.onNodeWithTag("music_lock_screen").performTouchInput { swipeRight() }
        assertEquals(1, dismiss)
    }

    @androidx.compose.runtime.Composable
    private fun TestPlayer(state: ListenUiState, actions: MutableList<String> = mutableListOf()) {
        ListenScreen(state, {}, {}, { actions += "toggle" }, {}, { actions += "seek:$it" },
            { actions += "previous" }, { actions += "next" }, {}, {}, {}, {}, {}, {}, {},
            onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
            onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
    }

    private fun assertLandscapePlayer(state: ListenUiState) {
        compose.onNodeWithTag("landscape_player").assertIsDisplayed()
        val artwork = compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player_controls").fetchSemanticsNode().boundsInRoot
        assertTrue(artwork.right < controls.left)
        val record = compose.onNodeWithContentDescription("黑胶唱片暂停，唱针已抬起").fetchSemanticsNode().boundsInRoot
        assertInside(record, artwork)
        assertTrue("Landscape record should remain useful", record.height > 280f)
        val title = compose.onNodeWithText(state.plannedPlayback!!.plan.item.title).assertIsDisplayed()
        assertInside(title.fetchSemanticsNode().boundsInRoot, controls)
        val episode = compose.onNodeWithTag("player_episode_count").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("player_controls_viewport").getUnclippedBoundsInRoot()
        assertTrue("The full episode count must be visible without scrolling: $episode in $viewport",
            episode.top >= viewport.top && episode.bottom <= viewport.bottom)
        listOf("播放设置", "上一条", "播放", "下一条", "播放队列", "前进 15 秒", "后退 15 秒", "特别收藏当前音频").forEach {
            val node = compose.onNodeWithContentDescription(it).assertIsDisplayed()
            assertInside(node.fetchSemanticsNode().boundsInRoot, controls)
        }
        compose.onNodeWithText("01:03").assertIsDisplayed()
        compose.onNodeWithText("04:00").assertIsDisplayed()
        assertHeaderBesideArtwork()
    }

    private fun assertHeaderBesideArtwork() {
        val header = compose.onNodeWithTag("player_header").fetchSemanticsNode().boundsInRoot
        val artwork = compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player_controls").fetchSemanticsNode().boundsInRoot
        assertEquals("Header and program controls must share their left edge", controls.left, header.left, 1f)
        assertEquals("Header and program controls must share their right edge", controls.right, header.right, 1f)
        assertEquals("Artwork must extend into the former header space", header.top, artwork.top, 1f)
        assertEquals("Artwork must span the whole right column's height", controls.bottom, artwork.bottom, 1f)
        assertTrue(header.bottom < controls.top && artwork.right < header.left)
        assertInside(compose.onNodeWithText("收藏夹").assertIsDisplayed().fetchSemanticsNode().boundsInRoot, header)
    }

    private fun assertInside(inner: Rect, outer: Rect) {
        assertTrue("$inner must fit inside $outer", inner.left >= outer.left - 1 && inner.right <= outer.right + 1 &&
            inner.top >= outer.top - 1 && inner.bottom <= outer.bottom + 1)
    }

    private fun saveImage(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val bitmap = Bitmap.createBitmap(views.first().width, views.first().height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            views.forEach { it.draw(canvas) }
            File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
