package com.bililisten.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bililisten.android.R
import com.bililisten.android.TestFixtures
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.ContentKey
import com.bililisten.android.data.SpecialFavorites
import com.bililisten.android.player.AudioPlaybackService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackWidgetTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun specialPlaylistSupportsWidgetSwitchingSkippingAndRemovalWithoutRestoringOldMembership() {
        val repository = AppGraph.repository(context)
        val a = TestFixtures.playback(listOf(TestFixtures.item(0)))
        val b = TestFixtures.playback(listOf(TestFixtures.item(0, collection = "season:other")
            .copy(key = ContentKey("BV1xx411c7mD", 11))))
        repository.toggleSpecialFavorite(a)
        repository.toggleSpecialFavorite(b)
        val special = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        repository.saveProgress(special, 42_000)
        val service = Robolectric.buildService(AudioPlaybackService::class.java).create()
        try {
            service.get().onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_COLLECTION)
                .putExtra(AudioPlaybackService.EXTRA_FAVORITE_ID, SpecialFavorites.ID), 0, 1)
            assertEquals(SpecialFavorites.ID, repository.activePlayback!!.queue.collectionId)
            assertEquals(42_000L, repository.activePlayback!!.plan.startPositionMs)
            service.get().onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_NEXT), 0, 2)
            assertEquals(b.plan.item.key, repository.activePlayback!!.plan.item.key)
            service.get().onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_PREVIOUS), 0, 3)
            assertEquals(a.plan.item.key, repository.activePlayback!!.plan.item.key)
            assertEquals(42_000L, repository.activePlayback!!.plan.startPositionMs)
            repository.toggleSpecialFavorite(a)
            AudioPlaybackService.updatePlaybackContext(context, special)
            service.get().onStartCommand(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService, 0, 4)
            assertEquals(a.plan.item.key, repository.activePlayback!!.plan.item.key) // Continue sounding.
            service.get().onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_NEXT), 0, 5)
            assertEquals(b.plan.item.key, repository.activePlayback!!.plan.item.key)
            assertEquals(1, repository.activePlayback!!.queue.items.size)
        } finally { service.destroy() }
        WidgetStateStore(context).save(WidgetPlaybackState(special)) // An old widget snapshot.
        assertEquals(b.plan.item.key, PlaybackWidgets.restoredPlayback(context)!!.plan.item.key)
        repository.toggleSpecialFavorite(b)
        assertNull(PlaybackWidgets.restoredPlayback(context))
    }

    @Test fun savedManualOrderUpdatesTheActiveServiceAndColdWidgetQueue() {
        val repository = AppGraph.repository(context)
        val items = (0..3).map { TestFixtures.item(it) }
        items.indices.forEach { repository.toggleSpecialFavorite(TestFixtures.playback(items, it)) }
        val old = repository.loadFavorite(SpecialFavorites.ID)!!.playback
        repository.saveProgress(old, 42_000)
        val controller = Robolectric.buildService(AudioPlaybackService::class.java).create()
        try {
            val service = controller.get()
            service.onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_COLLECTION)
                .putExtra(AudioPlaybackService.EXTRA_FAVORITE_ID, SpecialFavorites.ID), 0, 1)
            val manual = listOf(2, 0, 3, 1).map { items[it].key }
            repository.saveSpecialFavoriteOrder(manual)
            AudioPlaybackService.updatePlaybackContext(context, old) // Even a stale UI context uses the committed order.
            service.onStartCommand(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService, 0, 2)
            assertEquals(manual, repository.activePlayback!!.queue.items.map { it.key })
            assertEquals(items[0].key, repository.activePlayback!!.plan.item.key)
            assertEquals(42_000L, repository.activePlayback!!.plan.startPositionMs)
            service.onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_NEXT), 0, 3)
            assertEquals(items[3].key, repository.activePlayback!!.plan.item.key)
            service.onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_PREVIOUS), 0, 4)
            assertEquals(items[0].key, repository.activePlayback!!.plan.item.key)
            assertEquals(42_000L, repository.activePlayback!!.plan.startPositionMs)
        } finally { controller.destroy() }
        WidgetStateStore(context).save(WidgetPlaybackState(old))
        assertEquals(listOf(2, 0, 3, 1).map { items[it].key }, PlaybackWidgets.restoredPlayback(context)!!.queue.items.map { it.key })
    }

    @Test fun bothWidgetsRenderProgressAndButtonsDispatchDirectlyToTheService() {
        val playback = TestFixtures.playback()
        val state = WidgetPlaybackState(playback, positionMs = 60_000, durationMs = 240_000, playing = true)
        for (compact in listOf(false, true)) {
            val view = PlaybackWidgets.views(context, state, compact).apply(context, FrameLayout(context))
            assertEquals(playback.plan.item.title, view.findViewById<TextView>(R.id.widget_title).text)
            assertEquals(250, view.findViewById<ProgressBar>(R.id.widget_progress).progress)
            assertEquals(if (compact) "01:00 / 04:00" else "01:00", view.findViewById<TextView>(R.id.widget_time).text)
            if (!compact) assertEquals("04:00", view.findViewById<TextView>(R.id.widget_duration).text)
            assertEquals("暂停", view.findViewById<View>(R.id.widget_toggle).contentDescription)
            val actions = listOf(R.id.widget_previous to AudioPlaybackService.ACTION_WIDGET_PREVIOUS,
                R.id.widget_toggle to AudioPlaybackService.ACTION_WIDGET_TOGGLE,
                R.id.widget_next to AudioPlaybackService.ACTION_WIDGET_NEXT)
            actions.forEach { (id, action) ->
                assertTrue(view.findViewById<View>(id).performClick())
                val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
                assertEquals(action, intent.action)
                assertEquals(AudioPlaybackService::class.java.name, intent.component!!.className)
                assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
            }
            if (RuntimeEnvironment.getApiLevel() == 36) render(view, if (compact) 172 else 360, 180,
                if (compact) "widget-2x2" else "widget-4x2")
        }
    }

    @Test fun restoringWidgetAfterProcessDeathKeepsPositionButShowsPaused() {
        WidgetStateStore(context).save(WidgetPlaybackState(TestFixtures.playback(), 90_000, 240_000, playing = true))
        val state = WidgetStateStore(context).load()!!
        assertEquals(90_000L, state.positionMs)
        assertFalse(state.playing)
        assertFalse(state.playRequested)
        val view = PlaybackWidgets.views(context, state, true).apply(context, FrameLayout(context))
        assertEquals("播放", view.findViewById<View>(R.id.widget_toggle).contentDescription)
    }

    @Test fun compactWidgetKeepsItsControlsInsideASmallLauncherCell() {
        val state = WidgetPlaybackState(TestFixtures.playback(), positionMs = 45_000)
        val view = PlaybackWidgets.views(context, state, true, heightDp = 140).apply(context, FrameLayout(context))
        render(view, 140, 140, "widget-2x2-small")
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_header).visibility)
        val bounds = android.graphics.Rect()
        val toggle = view.findViewById<View>(R.id.widget_toggle)
        assertTrue(toggle.getGlobalVisibleRect(bounds))
        assertEquals(toggle.height, bounds.height())
        assertTrue(bounds.bottom <= view.height)
        assertTrue(view.findViewById<ProgressBar>(R.id.widget_progress).progress > 0)
    }

    @Test fun widgetCanColdStartThenSkipUsingTheStoredQueueAndRecentPosition() {
        val playback = TestFixtures.playback()
        val repository = AppGraph.repository(context)
        repository.saveProgress(playback, 50_000)
        val second = repository.planPlaybackFromQueue(playback.queue, 1)
        repository.saveProgress(second, 31_000)
        WidgetStateStore(context).save(WidgetPlaybackState(playback, positionMs = 50_000))
        val controller = Robolectric.buildService(AudioPlaybackService::class.java).create()
        try {
            val service = controller.get()
            assertEquals("Cold widget starts must register the media session without a UI controller", 1, service.sessions.size)
            service.onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_TOGGLE), 0, 1)
            assertEquals(playback.plan.item.key, repository.activePlayback!!.plan.item.key)
            assertEquals(50_000L, repository.activePlayback!!.plan.startPositionMs)
            service.onStartCommand(Intent(context, AudioPlaybackService::class.java)
                .setAction(AudioPlaybackService.ACTION_WIDGET_NEXT), 0, 2)
            assertEquals(second.plan.item.key, repository.activePlayback!!.plan.item.key)
            assertEquals(31_000L, repository.activePlayback!!.plan.startPositionMs)
        } finally {
            controller.destroy()
        }
    }

    @Test fun wideWidgetKeepsSquareCoverAndCenteredControlsAtMinimumSizeAndOpensThePicker() {
        val source = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(48, 118, 109)) }
        val square = PlaybackWidgets.cropCover(source, round = false)
        assertEquals(255, Color.alpha(square.getPixel(0, 0)))
        assertEquals(0, Color.alpha(PlaybackWidgets.cropCover(source, round = true).getPixel(0, 0)))
        for ((width, height) in listOf(360 to 180, 280 to 140)) {
            val state = WidgetPlaybackState(TestFixtures.playback(), positionMs = 3_661_000, durationMs = 7_200_000)
            val view = PlaybackWidgets.views(context, state, false, square, height).apply(context, FrameLayout(context))
            render(view, width, height, "widget-4x2-${width}x$height")
            fun bounds(id: Int) = Rect().also { assertTrue(view.findViewById<View>(id).getGlobalVisibleRect(it)) }
            val cover = bounds(R.id.widget_cover)
            assertEquals(cover.width(), cover.height())
            val controls = bounds(R.id.widget_transport)
            assertEquals(view.width / 2f, controls.exactCenterX(), 1f)
            assertTrue(bounds(R.id.widget_switch_collection).right <= controls.left)
            val progress = bounds(R.id.widget_progress)
            assertTrue(bounds(R.id.widget_time).right < progress.left)
            assertTrue(bounds(R.id.widget_duration).left > progress.right)
            assertTrue(cover.bottom <= progress.top)
            assertTrue(bounds(R.id.widget_toggle).bottom <= view.height)
            assertEquals("1:01:01", view.findViewById<TextView>(R.id.widget_time).text)
            assertEquals("2:00:00", view.findViewById<TextView>(R.id.widget_duration).text)
            view.findViewById<View>(R.id.widget_switch_collection).performClick()
            val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertEquals(FavoritePickerActivity::class.java.name, intent.component!!.className)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        }
    }

    @Test fun favoritePickerIsFloatingAndStartsTheSelectedCollectionAtItsLatestSavedPosition() {
        val repository = AppGraph.repository(context)
        val playback = TestFixtures.playback(index = 2)
        repository.saveFavorite(playback)
        repository.saveProgress(playback, 61_000)
        val id = repository.favoriteIdOf(playback)
        val activity = Robolectric.buildActivity(FavoritePickerActivity::class.java).setup()
        try {
            val attributes = activity.get().obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
            try { assertTrue(attributes.getBoolean(0, false)) } finally { attributes.recycle() }
            activity.get().selectFavorite(id)
            assertTrue(activity.get().isFinishing)
            val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
            assertEquals(AudioPlaybackService.ACTION_WIDGET_COLLECTION, intent.action)
            assertEquals(id, intent.getStringExtra(AudioPlaybackService.EXTRA_FAVORITE_ID))
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
            // Progress can advance after the popup opens. Resolve it inside the service, not in the popup.
            repository.saveProgress(playback, 73_000)
            val service = Robolectric.buildService(AudioPlaybackService::class.java).create()
            try {
                service.get().onStartCommand(intent, 0, 1)
                assertEquals(playback.plan.item.key, repository.activePlayback!!.plan.item.key)
                assertEquals(73_000L, repository.activePlayback!!.plan.startPositionMs)
                assertEquals(0L, repository.listeningTimeMs(id)) // Loading is not listening.
                val another = TestFixtures.playback((0..3).map { TestFixtures.item(it, collection = "season:456") }, index = 1)
                repository.saveFavorite(another)
                repository.saveProgress(another, 31_000)
                service.get().onStartCommand(Intent(context, AudioPlaybackService::class.java)
                    .setAction(AudioPlaybackService.ACTION_WIDGET_COLLECTION)
                    .putExtra(AudioPlaybackService.EXTRA_FAVORITE_ID, repository.favoriteIdOf(another)), 0, 2)
                assertEquals("season:456", repository.activePlayback!!.queue.collectionId)
                assertEquals(31_000L, repository.activePlayback!!.plan.startPositionMs)
                assertEquals(73_000L, repository.loadFavorite(id)!!.playback.plan.startPositionMs)
            } finally { service.destroy() }
        } finally { activity.pause().stop().destroy() }
    }

    private fun render(view: View, widthDp: Int, heightDp: Int, name: String) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/reports/ui/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
