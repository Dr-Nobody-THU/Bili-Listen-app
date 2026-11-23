package com.bililisten.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.*
import com.bililisten.android.widget.FavoritePickerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListenScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h740dp-xhdpi")
    fun currentPartHeartSitsLeftOfCollectionFavoriteAndOpensItsOwnPlaylist() {
        val repository = ListenRepository(org.robolectric.RuntimeEnvironment.getApplication())
        val playback = TestFixtures.playback()
        val state = mutableStateOf(ListenUiState(plannedPlayback = playback))
        var opened: String? = null
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onOpenFavorite = { opened = it }, onOpenCreatorCollections = {}, onOpenCreatorCollection = {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {}, onToggleSpecialFavorite = {
                    val current = state.value.plannedPlayback!!
                    repository.toggleSpecialFavorite(current)
                    state.value = state.value.copy(isCurrentSpecialFavorite = repository.isSpecialFavorited(current.plan.item.key),
                        favorites = repository.listFavorites())
                })
        }
        val heart = compose.onNodeWithContentDescription("特别收藏当前音频")
        heart.assertIsDisplayed().assertIsOff()
        val heartBounds = heart.fetchSemanticsNode().boundsInRoot
        val favoriteBounds = compose.onNodeWithText("加入收藏").fetchSemanticsNode().boundsInRoot
        assertTrue(heartBounds.right <= favoriteBounds.left)
        heart.performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, repository.loadFavorite(SpecialFavorites.ID)!!.itemCount)
            val p2 = TestFixtures.playback(index = 1)
            state.value = state.value.copy(plannedPlayback = p2, isCurrentSpecialFavorite = repository.isSpecialFavorited(p2.plan.item.key))
        }
        heart.assertIsOff()
        compose.runOnIdle { state.value = state.value.copy(plannedPlayback = playback,
            isCurrentSpecialFavorite = repository.isSpecialFavorited(playback.plan.item.key)) }
        heart.assertIsOn()
        compose.onNodeWithText("加入收藏").assertExists() // Article hearts do not favorite their source collection.
        saveImage("player-special-favorite", compose.onRoot())
        compose.onNodeWithText("收藏夹").performClick()
        compose.onNodeWithText("特别收藏").assertIsDisplayed()
        saveImage("special-favorites-drawer", compose.onRoot())
        compose.onNodeWithText("特别收藏").performClick()
        assertEquals(SpecialFavorites.ID, opened)
        heart.performClick().assertIsOff()
        compose.runOnIdle { assertTrue(repository.listFavorites().isEmpty()) }
    }

    @Test fun darkPlayerShowsActualSourceAndQueueCacheStates() {
        val playback = TestFixtures.playback((0..7).map { TestFixtures.item(it, 1_760_000_000_000L + it * 86_400_000L) })
        val state = mutableStateOf(ListenUiState(
            plannedPlayback = playback,
            playbackControl = PlaybackControlState(
                connected = true, mediaId = playback.plan.item.key.asStoreKey(), positionMs = 23_000L,
                durationMs = 240_000L, hasActiveItem = true, localAudio = true, isPlaying = false,
            ),
            cacheSnapshot = AudioCacheSnapshot(
                entries = playback.queue.items.associate { item -> item.key.asStoreKey() to when (item.orderInCollection) {
                    0, 1 -> AudioCacheEntry(CachePhase.CACHED, 12_000_000L, 12_000_000L)
                    2 -> AudioCacheEntry(CachePhase.DOWNLOADING, 3_000_000L, 10_000_000L)
                    else -> AudioCacheEntry(CachePhase.WAITING_WIFI)
                } }, wifiAvailable = true, targetCount = 6, readyTargetCount = 2, autoCacheEnabled = true,
            ),
        ))
        compose.setContent {
            ListenScreen(
                state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = { order ->
                    state.value = state.value.copy(currentCollectionPlaybackOrder = order,
                        plannedPlayback = playback.copy(queue = QueueOrdering.reorder(playback.queue, order)))
                },
                onAllowMobileStreamingChanged = {}, onRetryCache = {}, onFadePlaybackChanged = {}, onBackgroundAudioChanged = {},
            )
        }
        compose.onNodeWithText("本地缓存播放").assertExists()
        compose.onNodeWithText("缓存详情").assertDoesNotExist()
        compose.onNodeWithContentDescription("黑胶唱片暂停，唱针已抬起").assertExists()
        assertAnchoredPlayerLayout()
        saveImage("player-vinyl-paused", compose.onRoot())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(playbackControl = state.value.playbackControl.copy(isPlaying = true)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithContentDescription("黑胶唱片旋转，唱针已落下").assertExists()
        saveImage("player-vinyl-playing", compose.onRoot())
        compose.runOnIdle { state.value = state.value.copy(playbackControl = state.value.playbackControl.copy(isPlaying = false)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("播放队列").performScrollTo().performClick()
        compose.onNodeWithText("已缓存 2 条 · 22.9 MB").assertExists()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithText("时间倒序").performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.runOnIdle { assertEquals(CollectionPlaybackOrder.PUBLISHED_DESC, state.value.currentCollectionPlaybackOrder) }
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithText("合集顺序").performClick()
        compose.runOnIdle { assertEquals(playback.queue.items, state.value.plannedPlayback!!.queue.items) }
        val roots = compose.onAllNodes(isRoot())
        saveImage("queue-cache-dark", roots[roots.fetchSemanticsNodes().lastIndex])
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("queue_order_options").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))[0]
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithTag("playback_queue").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放队列").performScrollTo().performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h1000dp-xhdpi")
    fun tallPhoneKeepsControlsAtTheBottomAndCentersTheRecord() {
        showPreparedPlayer()
        assertAnchoredPlayerLayout()
        saveImage("player-layout-tall", compose.onRoot())
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-xhdpi")
    fun compactPhoneShrinksTheRecordWithoutHidingPlaybackControls() {
        showPreparedPlayer()
        assertAnchoredPlayerLayout()
        val artwork = compose.onNodeWithContentDescription("黑胶唱片暂停，唱针已抬起").fetchSemanticsNode().boundsInRoot
        val area = compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot
        assertTrue("The short screen should size the record by available height", artwork.width < area.width - 1f)
        saveImage("player-layout-compact", compose.onRoot())
    }

    @Test fun locateButtonReturnsToCurrentEpisodeWithoutBlankSpaceOrBounceAtTheEnd() {
        val playback = TestFixtures.playback((0..24).map { TestFixtures.item(it) }, index = 24)
        showPreparedPlayer(playback)
        compose.onNodeWithContentDescription("播放队列").performScrollTo().performClick()
        compose.onNodeWithContentDescription("定位当前播放").assertDoesNotExist()
        compose.onNodeWithTag("playback_queue").performScrollToIndex(0)
        compose.onNodeWithContentDescription("定位当前播放").assertIsDisplayed()
        val roots = compose.onAllNodes(isRoot())
        saveImage("queue-locate", roots[roots.fetchSemanticsNodes().lastIndex])
        compose.onNodeWithContentDescription("定位当前播放").performClick()
        compose.onNodeWithTag("queue_item_24").assertIsDisplayed()
        val list = compose.onNodeWithTag("playback_queue").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("queue_item_24").fetchSemanticsNode().boundsInRoot
        assertEquals("The last row must stop at the viewport bottom", list.bottom, row.bottom, 1f)
        compose.onNodeWithContentDescription("定位当前播放").assertDoesNotExist()
        repeat(3) {
            compose.onNodeWithTag("playback_queue").performTouchInput { swipeUp() }
            compose.mainClock.advanceTimeBy(1_000)
            val settled = compose.onNodeWithTag("queue_item_24").fetchSemanticsNode().boundsInRoot
            assertEquals(row.bottom, settled.bottom, 1f)
        }
        val endRoots = compose.onAllNodes(isRoot())
        saveImage("queue-bottom-stable", endRoots[endRoots.fetchSemanticsNodes().lastIndex])
    }

    @Test fun fadeSettingIsEnabledByDefaultAndPersistsWhenSwitchedOff() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val store = PlayerSettingsStore(context)
        val state = mutableStateOf(ListenUiState(plannedPlayback = TestFixtures.playback(), playerSettings = store.load()))
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onBackgroundAudioChanged = {},
                onFadePlaybackChanged = { state.value = state.value.copy(playerSettings = store.saveFadePlayback(it)) })
        }
        compose.onNodeWithContentDescription("播放设置").performScrollTo().performClick()
        compose.onNodeWithContentDescription("声音淡入淡出").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertTrue(!PlayerSettingsStore(context).load().fadePlayback) }
        val roots = compose.onAllNodes(isRoot())
        saveImage("settings-fade", roots[roots.fetchSemanticsNodes().lastIndex])
    }

    @Test fun skinSwitchPersistsWithoutChangingPlaybackSettingsAndCanBeReverted() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val store = PlayerSettingsStore(context)
        assertEquals(AppSkin.MINT_NIGHT, store.load().skin)
        val initialSettings = store.save(PlayerSettings(speed = 1.5f, playMode = PlayMode.SINGLE_LOOP,
            fadePlayback = false, musicLockScreen = true,
            backgroundAudio = BackgroundAudioSettings(enabled = true, volume = .3f)))
        val playback = TestFixtures.playback()
        val state = mutableStateOf(ListenUiState(plannedPlayback = playback, playerSettings = initialSettings,
            playbackControl = PlaybackControlState(connected = true, mediaId = playback.plan.item.key.asStoreKey(),
                positionMs = 23_000, durationMs = 240_000, hasActiveItem = true, localAudio = true)))
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {},
                onSkinChanged = { state.value = state.value.copy(playerSettings = store.saveSkin(it)) })
        }
        compose.onNodeWithContentDescription("播放设置").performScrollTo().performClick()
        compose.onNodeWithText("薄荷夜色").assertIsSelected()
        compose.onNodeWithText("黑蓝夜色").performClick().assertIsSelected()
        compose.onNodeWithText("薄荷夜色").assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(initialSettings.copy(skin = AppSkin.MIDNIGHT_BLUE), PlayerSettingsStore(context).load())
        }
        val roots = compose.onAllNodes(isRoot())
        saveImage("settings-skin-blue", roots[roots.fetchSemanticsNodes().lastIndex])
        val height = compose.onNodeWithTag("playback_settings_sheet").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("settings_sheet_drag_handle", useUnmergedTree = true).performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(centerX, height * .9f), 350)
        }
        compose.onNodeWithTag("playback_settings_sheet").assertDoesNotExist()
        assertAnchoredPlayerLayout()
        saveImage("player-midnight-blue", compose.onRoot())
        compose.onNodeWithContentDescription("播放设置").performScrollTo().performClick()
        compose.onNodeWithText("黑蓝夜色").assertIsSelected()
        compose.onNodeWithText("薄荷夜色").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(initialSettings, PlayerSettingsStore(context).load()) }
    }

    private fun showPreparedPlayer(playback: PlannedPlayback = TestFixtures.playback()) {
        val state = ListenUiState(plannedPlayback = playback, playbackControl = PlaybackControlState(
            connected = true, mediaId = playback.plan.item.key.asStoreKey(), positionMs = 23_000,
            durationMs = 240_000, hasActiveItem = true, localAudio = true))
        compose.setContent {
            ListenScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {}, onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
        }
    }

    @Test fun favoritesDrawerShowsSeparateListeningTotals() {
        val playback = TestFixtures.playback()
        val state = ListenUiState(plannedPlayback = playback, currentFavoriteId = "season:123",
            favorites = listOf(FavoriteCollection("season:123", playback, 0, 4_980_000),
                FavoriteCollection("season:456", playback.copy(queue = playback.queue.copy(collectionId = "season:456",
                    collectionTitle = "睡前的一段故事")), 0, 180_000)))
        compose.setContent {
            ListenScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {},
                onFadePlaybackChanged = {}, onBackgroundAudioChanged = {})
        }
        compose.onNodeWithText("收藏夹").performClick()
        compose.onNodeWithText("累计收听 1小时23分钟").assertIsDisplayed()
        compose.onNodeWithText("累计收听 3分钟").assertIsDisplayed()
        saveImage("favorites-listening-time", compose.onRoot())
    }

    @Test fun desktopCollectionPopupScrollsSelectsAndHandlesAnEmptyLibrary() {
        val favorites = mutableStateOf((0..14).map { index ->
            val playback = TestFixtures.playback()
            FavoriteCollection("season:$index", playback.copy(queue = playback.queue.copy(collectionId = "season:$index",
                collectionTitle = when (index) { 0 -> "把日常听成风景"; 1 -> "睡前的一段故事"; 2 -> "城市漫步与自然的声音"; else -> "我的收藏合集 $index" })),
                0, (index + 1) * 2_100_000L)
        })
        var selected: String? = null
        var dismissed = false
        var openedApp = false
        compose.setContent {
            MaterialTheme(colorScheme = ListenColorScheme) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0D151A)).padding(16.dp), contentAlignment = Alignment.Center) {
                    FavoritePickerContent(favorites.value, "season:0", { selected = it }, { dismissed = true }, { openedApp = true })
                }
            }
        }
        compose.onNodeWithText("累计收听 35分钟").assertIsDisplayed()
        saveImage("widget-favorite-picker", compose.onRoot())
        compose.onNodeWithTag("favorite_picker_list").performScrollToNode(hasText("我的收藏合集 14"))
        compose.onNodeWithText("我的收藏合集 14").performClick()
        compose.runOnIdle { assertEquals("season:14", selected) }
        compose.onNodeWithContentDescription("关闭合集选择").performClick()
        compose.runOnIdle { assertTrue(dismissed); favorites.value = emptyList() }
        compose.onNodeWithText("还没有收藏合集，先到应用中添加。").assertIsDisplayed()
        compose.onNodeWithText("打开应用").performClick()
        compose.runOnIdle { assertTrue(openedApp) }
    }

    @Test fun backgroundPickerOffersAllSoundsAndSavesChoiceVolumeAndOffState() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val store = PlayerSettingsStore(context)
        val state = mutableStateOf(ListenUiState(plannedPlayback = TestFixtures.playback(), playerSettings = store.load()))
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = {}, onAllowMobileStreamingChanged = {}, onRetryCache = {}, onFadePlaybackChanged = {},
                onBackgroundAudioChanged = { state.value = state.value.copy(playerSettings = store.saveBackgroundAudio(it)) })
        }
        compose.onNodeWithContentDescription("播放设置").performScrollTo().performClick()
        compose.onNodeWithContentDescription("启用背景音").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("选择背景音").performScrollTo().performClick()
        compose.onNodeWithText("棕噪声").performScrollTo().performClick()
        compose.onNodeWithContentDescription("启用背景音").assertIsOn()
        compose.onNodeWithContentDescription("背景音音量").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.35f) }
        compose.runOnIdle {
            assertEquals(BackgroundSound.BROWN, PlayerSettingsStore(context).load().backgroundAudio.sound)
            assertEquals(.35f, store.load().backgroundAudio.volume, .001f)
        }
        BackgroundSound.entries.forEach { sound ->
            compose.onAllNodesWithText(sound.label).filter(hasClickAction()).onFirst().performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("粉噪声").performScrollTo().performClick()
        val roots = compose.onAllNodes(isRoot())
        saveImage("background-sounds", roots[roots.fetchSemanticsNodes().lastIndex])
        compose.onNodeWithContentDescription("启用背景音").performClick().assertIsOff()
        compose.runOnIdle { assertTrue(!PlayerSettingsStore(context).load().backgroundAudio.enabled) }
        compose.onNodeWithText("音源与许可").performClick()
        compose.onNodeWithText("Qi Sheng background sound credits", substring = true).assertExists()
    }

    private fun assertAnchoredPlayerLayout() {
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一条").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一条").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithText("本地缓存播放").fetchSemanticsNode().boundsInRoot
        val area = compose.onNodeWithTag("player_artwork_area").fetchSemanticsNode().boundsInRoot
        val record = compose.onNodeWithContentDescription("黑胶唱片暂停，唱针已抬起").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player_controls").fetchSemanticsNode().boundsInRoot
        val density = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue("Footer should sit above the bottom padding, not leave a blank region", root.bottom - footer.bottom <= 40f * density)
        assertTrue(record.bottom <= controls.top)
        assertEquals(area.center.x, record.center.x, 1f)
        assertEquals(area.center.y, record.center.y, 1f)
        assertTrue("Record should use all available width or height", abs(record.width - area.width) <= 1f || abs(record.height - area.height) <= 1f)
    }

    private fun saveImage(name: String, node: SemanticsNodeInteraction) {
        node.assertExists()
        compose.waitForIdle()
        compose.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val view = views.first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            views.forEach { it.draw(canvas) }
            val file = File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
