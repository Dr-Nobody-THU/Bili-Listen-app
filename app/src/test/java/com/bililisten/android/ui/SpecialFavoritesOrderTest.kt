package com.bililisten.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h740dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecialFavoritesOrderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun specialQueueOffersFavoriteTimeAndManualSortingAndOnlyDoneCommitsTheDrag() {
        val repository = ListenRepository(RuntimeEnvironment.getApplication())
        val items = (0..3).map { TestFixtures.item(it, published = 0) }
        items.indices.forEach { repository.toggleSpecialFavorite(TestFixtures.playback(items, it)) }
        val originalKeys = items.map { it.key }
        val playback = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        val state = mutableStateOf(ListenUiState(plannedPlayback = playback,
            currentCollectionPlaybackOrder = CollectionPlaybackOrder.FAVORITED_ASC,
            specialFavoriteManualItems = repository.specialFavoriteManualItems()))
        var savedCount = 0
        fun refresh(order: CollectionPlaybackOrder) {
            val updated = repository.updateCollectionPlaybackOrder(state.value.plannedPlayback!!, order, 42_000)
            state.value = state.value.copy(plannedPlayback = updated, currentCollectionPlaybackOrder = order,
                specialFavoriteManualItems = repository.specialFavoriteManualItems())
        }
        compose.setContent {
            ListenScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onCollectionPlaybackOrderChanged = ::refresh, onAllowMobileStreamingChanged = {},
                onRetryCache = {}, onFadePlaybackChanged = {}, onBackgroundAudioChanged = {},
                onSaveSpecialFavoriteOrder = { keys ->
                    savedCount++
                    repository.saveSpecialFavoriteOrder(keys)
                    refresh(CollectionPlaybackOrder.MANUAL)
                })
        }
        compose.onNodeWithContentDescription("播放队列").performScrollTo().performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.onNodeWithText("时间正序").assertDoesNotExist()
        compose.onNodeWithText("时间倒序").assertDoesNotExist()
        compose.onNodeWithText("部分发布时间缺失", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_favorited_desc").performClick()
        compose.runOnIdle { assertEquals(originalKeys.reversed(), state.value.plannedPlayback!!.queue.items.map { it.key }) }
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_favorited_asc").performClick()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        saveImage("special-favorites-order-options")
        compose.onNodeWithTag("special_order_manual").performClick()
        dragBetween(items[0].key, items[2].key)
        compose.onNodeWithTag(row(items[0].key)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "第 3 项，共 4 项"))
        compose.runOnIdle {
            assertEquals(0, savedCount)
            assertEquals(originalKeys, repository.loadFavorite(SpecialFavorites.ID)!!.playback.queue.items.map { it.key })
        }
        compose.onNodeWithContentDescription("取消排序").performClick()
        compose.onNodeWithTag("queue_order_options").assertDoesNotExist()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_manual").performClick()
        compose.onNodeWithTag(row(items[0].key)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "第 1 项，共 4 项"))
        dragBetween(items[2].key, items[0].key)
        saveImage("special-favorites-manual-order")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("special_order_editor").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, savedCount)
            assertEquals(listOf(2, 0, 1, 3).map { items[it].key }, repository.loadFavorite(SpecialFavorites.ID)!!.playback.queue.items.map { it.key })
            assertEquals(items[0].key, state.value.plannedPlayback!!.plan.item.key)
            assertEquals(42_000L, state.value.plannedPlayback!!.plan.startPositionMs)
        }
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_favorited_desc").performClick()
        compose.onNodeWithTag("queue_order_toggle").performClick()
        compose.onNodeWithTag("special_order_manual").performClick()
        compose.onNodeWithTag(row(items[2].key)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "第 1 项，共 4 项"))
    }

    @Test fun holdingTheHandleAtTheListEdgeScrollsAndMovesPastTheInitiallyVisibleRows() {
        val items = (0 until 30).map { TestFixtures.item(it) }
        var saved: List<ContentKey>? = null
        compose.setContent {
            MaterialTheme(colorScheme = listenColorScheme(AppSkin.MIDNIGHT_BLUE)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SpecialFavoritesOrderEditor(items, items[0].key, {}, { saved = it })
                }
            }
        }
        val handle = compose.onNodeWithTag(handle(items[0].key))
        val handleBounds = handle.fetchSemanticsNode().boundsInRoot
        val listBounds = compose.onNodeWithTag("special_order_list").fetchSemanticsNode().boundsInRoot
        val distance = listBounds.bottom - handleBounds.center.y - 24f
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(600)
            repeat(12) { moveBy(Offset(0f, distance / 12f), delayMillis = 32) }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("special_order_list").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertTrue("Held edge drag should travel beyond the first viewport: $result", result.indexOf(items[0].key) >= 10)
            assertEquals(items.map { it.key }.toSet(), result.toSet())
            assertEquals(items.size, result.size)
        }
    }

    private fun dragBetween(from: ContentKey, to: ContentKey) {
        val source = compose.onNodeWithTag(handle(from))
        val distance = compose.onNodeWithTag(row(to)).fetchSemanticsNode().boundsInRoot.center.y -
            source.fetchSemanticsNode().boundsInRoot.center.y
        source.performTouchInput {
            down(center)
            advanceEventTime(600)
            repeat(12) { moveBy(Offset(0f, distance / 12f), delayMillis = 32) }
            up()
        }
        compose.waitForIdle()
    }

    private fun row(key: ContentKey) = "special_order_item_${key.asStoreKey()}"
    private fun handle(key: ContentKey) = "special_order_handle_${key.asStoreKey()}"

    private fun saveImage(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val bitmap = Bitmap.createBitmap(views.first().width, views.first().height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            views.forEach { it.draw(canvas) }
            File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
