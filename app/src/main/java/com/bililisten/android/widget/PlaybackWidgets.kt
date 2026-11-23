package com.bililisten.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.bililisten.android.MainActivity
import com.bililisten.android.R
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.PlaybackJsonCodec
import com.bililisten.android.data.SpecialFavorites
import com.bililisten.android.player.AudioPlaybackService
import kotlinx.coroutines.*

internal data class WidgetPlaybackState(
    val playback: PlannedPlayback? = null,
    val positionMs: Long = playback?.plan?.startPositionMs ?: 0L,
    val durationMs: Long = playback?.plan?.item?.durationMs ?: 0L,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val playRequested: Boolean = playing,
    val error: String? = null,
) {
    val progress: Int get() = if (durationMs > 0) (positionMs.coerceIn(0, durationMs) * 1000 / durationMs).toInt() else 0
}

/** Persist the queue and position, but never persist a potentially stale "playing" flag. */
internal class WidgetStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback_widget", Context.MODE_PRIVATE)
    fun save(state: WidgetPlaybackState) {
        val playback = state.playback ?: return
        prefs.edit().putString("playback", PlaybackJsonCodec.encode(playback.copy(
            plan = playback.plan.copy(startPositionMs = state.positionMs.coerceAtLeast(0L)))))
            .putLong("duration", state.durationMs).apply()
    }
    fun load(): WidgetPlaybackState? = runCatching {
        val raw = prefs.getString("playback", null) ?: return null
        val playback = PlaybackJsonCodec.decode(raw)
        WidgetPlaybackState(playback, durationMs = prefs.getLong("duration", playback.plan.item.durationMs))
    }.getOrNull()
}

open class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PlaybackWidgets.refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        PlaybackWidgets.refresh(context)
}

class WidePlaybackWidget : PlaybackWidgetProvider()
class CompactPlaybackWidget : PlaybackWidgetProvider()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlaybackWidgets {
    private var live: WidgetPlaybackState? = null
    private var artKey: String? = null
    private var artwork: Bitmap? = null
    private var artTask: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val providers = listOf(WidePlaybackWidget::class.java, CompactPlaybackWidget::class.java)

    fun restoredPlayback(context: Context): PlannedPlayback? {
        val repository = AppGraph.repository(context)
        val saved = WidgetStateStore(context).load()?.playback ?: repository.loadLastPlayback()
        return if (saved?.queue?.collectionId == SpecialFavorites.ID) repository.loadFavorite(SpecialFavorites.ID)?.playback else saved
    }

    internal fun state(context: Context): WidgetPlaybackState {
        val repository = AppGraph.repository(context) // Prune expired resume snapshots before reading them.
        return live ?: WidgetStateStore(context).load() ?: WidgetPlaybackState(repository.loadLastPlayback())
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return providers.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun publish(context: Context, value: WidgetPlaybackState, persist: Boolean = false) {
        val previous = live
        live = value
        if (persist) WidgetStateStore(context).save(value)
        if (previous == value) return
        if (!hasWidgets(context)) return
        val full = previous == null || previous.playback?.plan?.item != value.playback?.plan?.item ||
            previous.playRequested != value.playRequested || previous.loading != value.loading ||
            previous.error != value.error || previous.playing != value.playing ||
            previous.playback?.plan?.startIndex != value.playback?.plan?.startIndex
        update(context, value, full)
    }

    fun refresh(context: Context) = update(context.applicationContext, state(context), true)

    private fun update(context: Context, state: WidgetPlaybackState, full: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) return@forEach
            val compact = provider == CompactPlaybackWidget::class.java
            ids.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                    .takeIf { it > 0 } ?: 180
                val views = if (full) views(context, state, compact,
                    artwork.takeIf { artKey == state.playback?.plan?.item?.coverUrl }, height)
                    else RemoteViews(context.packageName, if (compact) R.layout.widget_player_compact else R.layout.widget_player_wide).apply {
                        applyProgress(this, state, compact)
                    }
                if (full) manager.updateAppWidget(id, views) else manager.partiallyUpdateAppWidget(id, views)
            }
        }
        if (full) loadArtwork(context, state)
    }

    private fun loadArtwork(context: Context, state: WidgetPlaybackState) {
        val url = state.playback?.plan?.item?.coverUrl
        if (url == artKey) return
        artTask?.cancel()
        artKey = url
        artwork = null
        if (url == null) return
        artTask = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url)
                        .size(160, 160).allowHardware(false)
                        .networkCachePolicy(if (AppGraph.repository(context).isWifiConnected()) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .build()).image?.toBitmap()?.let { cropCover(it, round = false) }
                }.getOrNull()
            }
            if (artKey == url && result != null) {
                artwork = result
                update(context, this@PlaybackWidgets.state(context), true)
            }
        }
    }

    internal fun cropCover(source: Bitmap, round: Boolean): Bitmap {
        val side = 160
        val scale = side.toFloat() / minOf(source.width, source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((side - source.width * scale) / 2, (side - source.height * scale) / 2)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            if (round) canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)
            else canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
        }
    }

    internal fun views(context: Context, state: WidgetPlaybackState, compact: Boolean, cover: Bitmap? = null, heightDp: Int = 180): RemoteViews {
        val playback = state.playback
        val item = playback?.plan?.item
        val openApp = PendingIntent.getActivity(context, 40, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteViews(context.packageName, if (compact) R.layout.widget_player_compact else R.layout.widget_player_wide).apply {
            if (compact) setViewVisibility(R.id.widget_header, if (heightDp < 170) View.GONE else View.VISIBLE)
            if (heightDp < 170) {
                setInt(R.id.widget_title, "setMaxLines", 1)
                if (!compact) setViewVisibility(R.id.widget_status, View.GONE)
            }
            if (!compact) {
                // Keep a square cover even in a 140 dp-high launcher cell.
                val side = ((heightDp - 96).coerceIn(44, 68) * context.resources.displayMetrics.density).toInt()
                setInt(R.id.widget_cover, "setMaxWidth", side)
                setInt(R.id.widget_cover, "setMaxHeight", side)
                setOnClickPendingIntent(R.id.widget_switch_collection, PendingIntent.getActivity(context, 41,
                    Intent(context, FavoritePickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            setOnClickPendingIntent(R.id.widget_content, openApp)
            setTextViewText(R.id.widget_title, item?.title ?: "随时听，接着听")
            setTextViewText(R.id.widget_subtitle, if (playback != null)
                "${playback.plan.startIndex + 1}/${playback.queue.items.size} · ${item?.authorName ?: playback.queue.collectionTitle ?: "栖声"}"
                else "先在应用中选择节目")
            val status = when {
                state.error != null -> "播放失败 · 点此查看"
                state.loading -> "正在加载…"
                state.playing -> "正在播放"
                playback != null -> "点击继续收听"
                else -> "栖声"
            }
            setTextViewText(R.id.widget_status, status)
            setContentDescription(R.id.widget_content, state.error ?: "打开播放器")
            if (cover == null) setImageViewResource(R.id.widget_cover, R.drawable.widget_record)
            else setImageViewBitmap(R.id.widget_cover, if (compact) cropCover(cover, round = true) else cover)
            setImageViewResource(R.id.widget_toggle, if (state.playRequested) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            setContentDescription(R.id.widget_toggle, if (state.playRequested) "暂停" else "播放")
            val commands = listOf(R.id.widget_previous to AudioPlaybackService.ACTION_WIDGET_PREVIOUS,
                R.id.widget_toggle to AudioPlaybackService.ACTION_WIDGET_TOGGLE,
                R.id.widget_next to AudioPlaybackService.ACTION_WIDGET_NEXT)
            commands.forEachIndexed { index, (id, action) ->
                setOnClickPendingIntent(id, if (playback == null) openApp else PendingIntent.getForegroundService(
                    context, 50 + index, Intent(context, AudioPlaybackService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            val canSkip = (playback?.queue?.items?.size ?: 0) > 1
            listOf(R.id.widget_previous, R.id.widget_next).forEach { id ->
                setBoolean(id, "setEnabled", canSkip)
                setInt(id, "setImageAlpha", if (canSkip) 255 else 80)
            }
            applyProgress(this, state, compact)
        }
    }

    private fun applyProgress(views: RemoteViews, state: WidgetPlaybackState, compact: Boolean) = with(views) {
        setProgressBar(R.id.widget_progress, 1000, state.progress, false)
        setTextViewText(R.id.widget_time, if (compact) "${time(state.positionMs)} / ${time(state.durationMs)}" else time(state.positionMs))
        if (!compact) setTextViewText(R.id.widget_duration, time(state.durationMs))
        setContentDescription(R.id.widget_progress, "播放进度 ${state.progress / 10}%")
    }

    private fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
            else "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}
