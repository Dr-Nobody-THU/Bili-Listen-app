package com.bililisten.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.bililisten.android.MainActivity
import com.bililisten.android.R
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.SpecialFavorites
import com.bililisten.android.data.PlaybackJsonCodec
import com.bililisten.android.lockscreen.MusicLockScreenLauncher
import com.bililisten.android.widget.PlaybackWidgets
import com.bililisten.android.widget.WidgetPlaybackState
import java.util.concurrent.Executors
import java.util.concurrent.Future

@UnstableApi
class AudioPlaybackService : MediaSessionService() {
    private val repository by lazy { AppGraph.repository(applicationContext) }
    private val logger by lazy { AppGraph.logger(applicationContext) }
    private val settingsStore by lazy { AppGraph.playerSettings(applicationContext) }
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val cacheExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectivityManager: ConnectivityManager? = null
    private var cacheTask: Future<*>? = null
    private var resolveTask: Future<*>? = null
    private lateinit var player: ExoPlayer
    private lateinit var volumeFader: PlaybackVolumeFader
    private val backgroundProcessor = BackgroundAudioProcessor()
    private lateinit var backgroundAudio: BackgroundAudioController
    private lateinit var sessionPlayer: QueueSessionPlayer
    private lateinit var mediaSession: MediaSession
    private var currentPlayback: PlannedPlayback? = null
    private var resolveGeneration = 0L
    private var retryCount = 0
    private var resolving = false
    private var requestedPlay = true
    private var destroyed = false
    private var lastWifiAvailable = false
    private var musicLockScreenLauncher: MusicLockScreenLauncher? = null
    private val listeningTime by lazy { ListeningTimeTracker(SystemClock::elapsedRealtime, repository::addListeningTime) }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            mainHandler.post { handleNetworkChange() }
        }
        override fun onLost(network: Network) {
            mainHandler.post { handleNetworkChange() }
        }
    }

    private val checkpoint = object : Runnable {
        override fun run() {
            flushProgress()
            publishWidget(persist = true)
            mainHandler.postDelayed(this, 5_000L)
        }
    }

    private val widgetTicker = object : Runnable {
        override fun run() {
            if (destroyed || !player.isPlaying) return
            if (getSystemService(PowerManager::class.java).isInteractive &&
                (PlaybackWidgets.hasWidgets(this@AudioPlaybackService) || settingsStore.load().musicLockScreen)) {
                publishWidget()
            }
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (destroyed) return
            val playback = currentPlayback?.takeIf {
                !resolving && player.currentMediaItem?.mediaId == it.plan.item.key.asStoreKey()
            }
            listeningTime.update(playback?.let(repository::favoriteIdOf), isPlaying)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (destroyed) return
            // A volume ramp must not persist widget JSON or restart its ticker on every frame.
            if (events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                flushProgress()
            }
            publishWidget(persist = true)
            mainHandler.removeCallbacks(widgetTicker)
            if (player.isPlaying) mainHandler.postDelayed(widgetTicker, 1_000L)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && player.repeatMode != Player.REPEAT_MODE_ONE) skip(1)
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(TAG, "Playback failed: code=${error.errorCodeName}, local=${isPlayingLocal()}.", error)
            val playback = currentPlayback ?: return
            val position = player.currentPosition.coerceAtLeast(0)
            val local = isPlayingLocal()
            if (local && PlaybackFailurePolicy.invalidatesLocalCache(error)) repository.invalidateCachedAudio(playback.plan.item)
            if (retryCount++ < 1) {
                startResolvedPlayback(playback.copy(plan = playback.plan.copy(startPositionMs = position)), resetRetry = false, play = requestedPlay)
            } else {
                failPlayback(PlaybackFailurePolicy.message(error, local))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "音频播放", NotificationManager.IMPORTANCE_LOW),
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .build().apply { setSmallIcon(R.drawable.ic_stat_listen) },
        )
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
        val sourceFactory = DefaultDataSource.Factory(this, httpFactory)
        backgroundAudio = BackgroundAudioController(
            backgroundProcessor,
            loadLoop = { BackgroundAudioController.loadPackagedLoop(this, it) },
            onError = { error ->
                logger.error(TAG, "Cannot load background audio.", error)
                mainHandler.post {
                    if (!destroyed) android.widget.Toast.makeText(this, "背景音加载失败，节目继续播放", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParameters: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(BackgroundAudioProcessorChain(backgroundProcessor))
                    .setEnableFloatOutput(false)
                    .setEnableAudioOutputPlaybackParameters(false)
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(
                PlaybackDataSource.Factory(sourceFactory) { repository.isWifiConnected() || settingsStore.load().allowMobileStreaming },
            ))
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
                    AudioOffloadPreferences.Builder().setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build(),
                ).build()
                addListener(listener)
            }
        volumeFader = PlaybackVolumeFader(player, mainHandler) {
            if (::sessionPlayer.isInitialized) sessionPlayer.refreshQueue()
            if (!destroyed) publishWidget(persist = true)
        }
        sessionPlayer = QueueSessionPlayer(
            player,
            canSkip = { (currentPlayback?.queue?.items?.size ?: 0) > 1 },
            onSkip = ::skip,
            onResume = {
                val playback = currentPlayback
                if (player.playbackState == Player.STATE_IDLE && playback != null && !resolving) {
                    startResolvedPlayback(playback)
                    true
                } else false
            },
            onPlayRequest = { requestedPlay = it },
            onSetPlayWhenReady = volumeFader::requestPlayWhenReady,
            playWhenReadyOverride = { volumeFader.playWhenReadyOverride },
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(mainActivityIntent())
            .setMediaButtonPreferences(listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setDisplayName("上一条").setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS).build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("下一条").setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT).build(),
            ))
            .build()
        // Widget/picker cold starts may have no UI controller to register the session for us.
        addSession(mediaSession)
        applySettings()
        mainHandler.postDelayed(checkpoint, 5_000L)
        lastWifiAvailable = repository.isWifiConnected()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        musicLockScreenLauncher = MusicLockScreenLauncher(this) {
            currentPlayback != null && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                if (resolving) requestedPlay else volumeFader.playRequested && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE, ACTION_WIDGET_PREVIOUS, ACTION_WIDGET_NEXT -> handleWidgetAction(intent.action!!)
            ACTION_WIDGET_COLLECTION -> {
                flushProgress()
                val favorite = intent.getStringExtra(EXTRA_FAVORITE_ID)?.let(repository::loadFavorite)
                // A widget picker is entirely local; use the latest saved episode/position at tap time.
                val playback = favorite?.playback
                startInForeground(playback?.plan?.item?.title ?: "栖声")
                if (playback != null) {
                    val active = currentPlayback
                    if (active != null && repository.favoriteIdOf(active) == favorite.id &&
                        player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                        requestedPlay = true
                        if (!resolving) {
                            sessionPlayer.play()
                            triggerNotificationUpdate()
                        }
                    } else {
                        val position = playback.plan.startPositionMs.takeUnless {
                            playback.plan.item.durationMs > 0 && it >= playback.plan.item.durationMs - 3_000L
                        } ?: 0L
                        startResolvedPlayback(playback.copy(plan = playback.plan.copy(startPositionMs = position)))
                    }
                } else if (currentPlayback == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    triggerNotificationUpdate()
                }
            }
            ACTION_START -> intent.getStringExtra(EXTRA_PLAYBACK)?.let {
                flushProgress()
                val playback = PlaybackJsonCodec.decode(it)
                startInForeground(playback.plan.item.title)
                startResolvedPlayback(playback)
            }
            ACTION_UPDATE_CONTEXT -> intent.getStringExtra(EXTRA_PLAYBACK)?.let {
                val update = PlaybackJsonCodec.decode(it)
                val active = currentPlayback
                // Do not let a delayed UI update replace a track already changed by a headset.
                if (active != null && active.queue.collectionId == update.queue.collectionId) {
                    val latest = if (active.queue.collectionId == SpecialFavorites.ID) repository.normalizePlaybackOrder(active) else update
                    val index = latest.queue.items.indexOfFirst { item -> item.key == active.plan.item.key }
                    if (index >= 0) {
                        currentPlayback = latest.copy(plan = active.plan.copy(startIndex = index, item = latest.queue.items[index]))
                        repository.activePlayback = currentPlayback
                        repository.saveLastPlayback(currentPlayback!!, player.currentPosition.coerceAtLeast(0))
                        sessionPlayer.refreshQueue()
                        scheduleCache()
                        publishWidget(persist = true)
                    }
                }
            }
            ACTION_RETRY_CACHE -> {
                val playback = intent.getStringExtra(EXTRA_PLAYBACK)?.let(PlaybackJsonCodec::decode)
                scheduleCache(playback)
            }
            ACTION_SETTINGS -> {
                applySettings()
                if (intent.getBooleanExtra(EXTRA_RECHECK_NETWORK, true)) handleNetworkChange(force = true)
            }
        }
        // Media3 also receives system/headset intents here.
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleWidgetAction(action: String) {
        val playback = currentPlayback ?: PlaybackWidgets.restoredPlayback(this)
        // A foreground-service PendingIntent must always be acknowledged, including an empty state.
        startInForeground(playback?.plan?.item?.title ?: "栖声")
        if (playback == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (currentPlayback == null) {
            val ordered = repository.normalizePlaybackOrder(playback)
            val delta = when (action) { ACTION_WIDGET_PREVIOUS -> -1; ACTION_WIDGET_NEXT -> 1; else -> 0 }
            val restored = if (delta != 0) repository.adjacentPlayback(ordered, delta) ?: ordered
            else ordered.copy(plan = ordered.plan.copy(startPositionMs = ordered.plan.startPositionMs.takeUnless {
                ordered.plan.item.durationMs > 0 && it >= ordered.plan.item.durationMs - 3_000L
            } ?: 0L))
            startResolvedPlayback(restored)
        } else when (action) {
            ACTION_WIDGET_PREVIOUS -> skip(-1)
            ACTION_WIDGET_NEXT -> skip(1)
            else -> when {
                resolving -> { requestedPlay = !requestedPlay; publishWidget() }
                player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED -> startResolvedPlayback(playback)
                volumeFader.playRequested -> sessionPlayer.pause()
                else -> sessionPlayer.play()
            }
        }
    }

    private fun startResolvedPlayback(playback: PlannedPlayback, resetRetry: Boolean = true, play: Boolean = true) {
        listeningTime.stop()
        resolveTask?.cancel(true)
        resolveGeneration++
        val generation = resolveGeneration
        currentPlayback = repository.normalizePlaybackOrder(playback)
        repository.activePlayback = currentPlayback
        repository.playbackError = null
        repository.playbackLoading = true
        resolving = true
        requestedPlay = play
        if (resetRetry) retryCount = 0
        applySettings()
        sessionPlayer.refreshQueue()
        // Stop the previous source before resolving the next one; do not keep fetching it.
        volumeFader.pauseImmediately()
        player.stop()
        player.clearMediaItems()
        publishWidget(persist = true)
        val requested = currentPlayback!!
        resolveTask = ioExecutor.submit {
            try {
                val local = repository.cachedAudioUri(requested.plan.item)
                if (local == null && !repository.isWifiConnected() && !settingsStore.load().allowMobileStreaming) {
                    throw IllegalStateException("这条音频尚未缓存。连接 Wi-Fi 后重试，或在播放设置中允许移动网络在线播放。")
                }
                val uri = local ?: Uri.parse(repository.resolveAudio(requested).selected.url)
                mainHandler.post {
                    if (destroyed || generation != resolveGeneration) return@post
                    if (uri.scheme != "file" && !repository.isWifiConnected() && !settingsStore.load().allowMobileStreaming) {
                        failPlayback("Wi-Fi 已断开，这条音频尚未缓存，已停止在线播放。")
                        return@post
                    }
                    // An order change during URL resolution must be kept.
                    val effective = currentPlayback ?: return@post
                    resolving = false
                    repository.playbackLoading = false
                    val newestLocal = repository.cachedAudioUri(effective.plan.item)
                    setSource(effective, newestLocal ?: uri, effective.plan.startPositionMs, play = requestedPlay)
                    repository.saveProgress(effective, effective.plan.startPositionMs)
                    repository.rememberSession(effective)
                    repository.saveLastPlayback(effective)
                    scheduleCache()
                }
            } catch (exception: Exception) {
                mainHandler.post {
                    if (destroyed || generation != resolveGeneration) return@post
                    logger.error(TAG, "Failed to resolve audio.", exception)
                    failPlayback(exception.message ?: "音频加载失败，请重试")
                }
            }
        }
    }

    private fun setSource(playback: PlannedPlayback, uri: Uri, position: Long, play: Boolean, switchingToCache: Boolean = false) {
        val local = uri.scheme == "file"
        val extras = Bundle().apply { putBoolean(EXTRA_LOCAL_AUDIO, local) }
        val metadata = MediaMetadata.Builder()
            .setTitle(playback.plan.item.title)
            .setArtist(playback.plan.item.authorName)
            .setAlbumTitle(playback.queue.collectionTitle)
            .setExtras(extras)
        // System artwork loaders must not fetch a remote cover during offline playback.
        if (repository.isWifiConnected()) {
            playback.plan.item.coverUrl?.let { metadata.setArtworkUri(Uri.parse(it)) }
        }
        val item = MediaItem.Builder().setUri(uri)
            .setMediaId(playback.plan.item.key.asStoreKey())
            .setMediaMetadata(metadata.build()).build()
        if (!switchingToCache) volumeFader.pauseImmediately()
        player.setMediaItem(item, position)
        player.prepare()
        // A network-to-file handoff keeps the current gain and any in-flight transition.
        if (!switchingToCache) volumeFader.requestPlayWhenReady(play)
        logger.info(TAG, "Audio source=${if (local) "LOCAL" else "NETWORK"}, item=${item.mediaId}")
    }

    private fun switchToCache(): Boolean {
        val playback = currentPlayback ?: return false
        if (resolving || player.currentMediaItem?.mediaId != playback.plan.item.key.asStoreKey()) return false
        if (isPlayingLocal()) return true
        val uri = repository.cachedAudioUri(playback.plan.item) ?: return false
        val position = player.currentPosition.coerceAtLeast(0L)
        setSource(playback, uri, position, volumeFader.playRequested, switchingToCache = true)
        return true
    }

    private fun isPlayingLocal(): Boolean = player.currentMediaItem?.localConfiguration?.uri?.scheme == "file"

    private fun skip(delta: Int) {
        val playback = currentPlayback ?: return
        flushProgress()
        val next = repository.adjacentPlayback(playback, delta) ?: return
        startInForeground(next.plan.item.title)
        startResolvedPlayback(next)
    }

    private fun scheduleCache(requestedPlayback: PlannedPlayback? = null) {
        val playback = requestedPlayback ?: currentPlayback ?: return
        cacheTask?.cancel(true)
        cacheTask = cacheExecutor.submit {
            val result = repository.maintainAutoCache(playback) { item ->
                mainHandler.post {
                    if (!destroyed && currentPlayback?.plan?.item?.key == item.key) switchToCache()
                }
            }
            logger.info(TAG, "Cache: Wi-Fi=${result.wifiAvailable}, ready=${result.cachedCount}/${result.targetCount}")
        }
    }

    private fun handleNetworkChange(force: Boolean = false) {
        if (destroyed) return
        val wifi = repository.isWifiConnected()
        if (!force && wifi == lastWifiAvailable) return
        lastWifiAvailable = wifi
        if (wifi) {
            scheduleCache()
        } else {
            cacheTask?.cancel(true)
            val playback = currentPlayback ?: return
            if (!switchToCache() && !settingsStore.load().allowMobileStreaming &&
                (player.playbackState != Player.STATE_IDLE || resolving)) {
                val position = if (player.currentMediaItem?.mediaId == playback.plan.item.key.asStoreKey())
                    player.currentPosition.coerceAtLeast(0) else playback.plan.startPositionMs
                currentPlayback = playback.copy(plan = playback.plan.copy(startPositionMs = position))
                failPlayback("当前音频尚未缓存，已停止在线播放。连接 Wi-Fi 或在播放设置中允许移动网络播放。")
            }
        }
    }

    private fun failPlayback(message: String) {
        resolveGeneration++
        resolving = false
        repository.playbackLoading = false
        repository.playbackError = message
        flushProgress()
        volumeFader.pauseImmediately()
        player.stop()
        player.playWhenReady = false
        publishWidget(persist = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun flushProgress() {
        listeningTime.checkpoint()
        val playback = currentPlayback ?: return
        if (player.currentMediaItem?.mediaId != playback.plan.item.key.asStoreKey() || resolving) return
        val position = player.currentPosition.coerceAtLeast(0L)
        currentPlayback = playback.copy(plan = playback.plan.copy(startPositionMs = position))
        repository.activePlayback = currentPlayback
        runCatching { repository.saveProgress(currentPlayback!!, position) }
            .onFailure { logger.error(TAG, "Cannot save playback progress.", it) }
    }

    private fun publishWidget(persist: Boolean = false, stopped: Boolean = false) {
        val playback = currentPlayback ?: return
        val matched = player.currentMediaItem?.mediaId == playback.plan.item.key.asStoreKey()
        PlaybackWidgets.publish(this, WidgetPlaybackState(
            playback = playback,
            positionMs = if (matched && !resolving) player.currentPosition.coerceAtLeast(0L) else playback.plan.startPositionMs,
            durationMs = if (matched) player.duration.takeIf { it > 0L } ?: playback.plan.item.durationMs else playback.plan.item.durationMs,
            playing = !stopped && volumeFader.playRequested && player.isPlaying,
            loading = !stopped && (resolving || player.playbackState == Player.STATE_BUFFERING),
            playRequested = !stopped && if (resolving) requestedPlay else volumeFader.playRequested && player.playbackState != Player.STATE_IDLE,
            error = repository.playbackError,
        ), persist)
    }

    private fun applySettings() {
        val settings = settingsStore.load()
        volumeFader.setEnabled(settings.fadePlayback)
        backgroundAudio.update(settings.backgroundAudio)
        player.playbackParameters = PlaybackParameters(settings.speed)
        player.repeatMode = if (settings.playMode == PlayMode.SINGLE_LOOP) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun startInForeground(title: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_listen)
            .setContentTitle(title).setContentText("正在加载音频…")
            .setContentIntent(mainActivityIntent()).setOnlyAlertOnce(true).setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        flushProgress()
        if (!player.playWhenReady && !resolving) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        destroyed = true
        resolveGeneration++
        flushProgress()
        listeningTime.stop()
        publishWidget(persist = true, stopped = true)
        mainHandler.removeCallbacksAndMessages(null)
        musicLockScreenLauncher?.close()
        connectivityManager?.let { runCatching { it.unregisterNetworkCallback(networkCallback) } }
        cacheTask?.cancel(true)
        resolveTask?.cancel(true)
        ioExecutor.shutdownNow()
        cacheExecutor.shutdownNow()
        repository.activePlayback = null
        repository.playbackLoading = false
        player.removeListener(listener)
        volumeFader.close()
        backgroundAudio.close()
        mediaSession.release()
        sessionPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.bililisten.android.action.WIDGET_TOGGLE"
        const val ACTION_WIDGET_PREVIOUS = "com.bililisten.android.action.WIDGET_PREVIOUS"
        const val ACTION_WIDGET_NEXT = "com.bililisten.android.action.WIDGET_NEXT"
        internal const val ACTION_WIDGET_COLLECTION = "com.bililisten.android.action.WIDGET_COLLECTION"
        internal const val EXTRA_FAVORITE_ID = "favorite_id"
        const val EXTRA_LOCAL_AUDIO = "local_audio"
        private const val TAG = "AudioPlaybackService"
        private const val ACTION_START = "com.bililisten.android.action.START_PLAYBACK"
        private const val ACTION_UPDATE_CONTEXT = "com.bililisten.android.action.UPDATE_PLAYBACK_CONTEXT"
        private const val ACTION_RETRY_CACHE = "com.bililisten.android.action.RETRY_CACHE"
        private const val ACTION_SETTINGS = "com.bililisten.android.action.UPDATE_SETTINGS"
        private const val EXTRA_PLAYBACK = "playback_json"
        private const val EXTRA_RECHECK_NETWORK = "recheck_network"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, playback: PlannedPlayback) {
            context.startForegroundService(Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLAYBACK, PlaybackJsonCodec.encode(playback))
            })
        }

        internal fun playFavorite(context: Context, favoriteId: String) {
            context.startForegroundService(Intent(context, AudioPlaybackService::class.java)
                .setAction(ACTION_WIDGET_COLLECTION).putExtra(EXTRA_FAVORITE_ID, favoriteId))
        }
        fun updatePlaybackContext(context: Context, playback: PlannedPlayback) {
            context.startService(Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE_CONTEXT
                putExtra(EXTRA_PLAYBACK, PlaybackJsonCodec.encode(playback))
            })
        }
        fun retryCache(context: Context, playback: PlannedPlayback) {
            context.startService(Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_RETRY_CACHE
                putExtra(EXTRA_PLAYBACK, PlaybackJsonCodec.encode(playback))
            })
        }
        fun updateSettings(context: Context, recheckNetwork: Boolean = true) {
            context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_SETTINGS)
                .putExtra(EXTRA_RECHECK_NETWORK, recheckNetwork))
        }
    }
}
