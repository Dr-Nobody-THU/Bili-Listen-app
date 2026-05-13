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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bililisten.android.MainActivity
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.PlaybackJsonCodec
import com.bililisten.android.data.PlayerSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class AudioPlaybackService : MediaSessionService() {
    private val repository by lazy { AppGraph.repository(applicationContext) }
    private val logger by lazy { AppGraph.logger(applicationContext) }
    private val playerSettingsStore by lazy { AppGraph.playerSettings(applicationContext) }
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cacheExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val checkpointHandler = Handler(Looper.getMainLooper())
    private var connectivityManager: ConnectivityManager? = null

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private var currentPlayback: PlannedPlayback? = null
    private var currentSettings: PlayerSettings = PlayerSettings()
    private var resolveGeneration: Long = 0L
    private var retryCountForCurrentItem: Int = 0

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                currentPlayback?.let(::scheduleAutoCacheMaintenance)
            }
        }
    }

    private val checkpointRunnable = object : Runnable {
        override fun run() {
            flushProgress()
            checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            logger.info(
                TAG,
                "Playback state changed to ${playbackStateLabel(playbackState)}.",
            )
            if (playbackState == Player.STATE_ENDED && player.repeatMode != Player.REPEAT_MODE_ONE) {
                advanceToNext()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            logger.info(TAG, "isPlaying changed to $isPlaying.")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error on current item", error)
            logger.error(TAG, "Player error on current item.", error)
            retryCurrentItem()
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.info(TAG, "Service created.")
        createNotificationChannel()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BILIBILI_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to BILIBILI_REFERER,
                    "User-Agent" to BILIBILI_USER_AGENT,
                ),
            )
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                addListener(playerListener)
            }
        currentSettings = playerSettingsStore.load()
        applyPlayerSettings(currentSettings)
        mediaSession = MediaSession.Builder(this, player).build()
        checkpointHandler.postDelayed(checkpointRunnable, CHECKPOINT_INTERVAL_MS)
        registerNetworkCallback()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PLAYBACK -> {
                val raw = intent.getStringExtra(EXTRA_PLAYBACK_JSON)
                    ?: return START_NOT_STICKY
                val playback = PlaybackJsonCodec.decode(raw)
                currentSettings = playerSettingsStore.load()
                applyPlayerSettings(currentSettings)
                logger.info(
                    TAG,
                    "Received start command for item=${playback.plan.item.title}, index=${playback.plan.startIndex}, position=${playback.plan.startPositionMs}ms",
                )
                startInForeground(
                    title = playback.plan.item.title,
                    text = "正在解析音频...",
                )
                startResolvedPlayback(playback)
            }

            ACTION_SKIP_NEXT -> {
                logger.info(TAG, "Received skip-next command.")
                advanceToNext()
            }

            ACTION_UPDATE_PLAYBACK_CONTEXT -> {
                val raw = intent.getStringExtra(EXTRA_PLAYBACK_JSON)
                    ?: return START_NOT_STICKY
                val playback = PlaybackJsonCodec.decode(raw)
                logger.info(
                    TAG,
                    "Received playback-context update for item=${playback.plan.item.title}, index=${playback.plan.startIndex}.",
                )
                currentPlayback = playback
                repository.rememberSession(playback)
                repository.saveLastPlayback(playback, player.currentPosition.coerceAtLeast(0L))
                scheduleAutoCacheMaintenance(playback)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        flushProgress()
        if (!player.playWhenReady) {
            logger.info(TAG, "Task removed while player not active. Stopping service.")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        logger.info(
            TAG,
            "Service destroyed. currentItem=${currentPlayback?.plan?.item?.title ?: "none"}, playWhenReady=${player.playWhenReady}, position=${player.currentPosition.coerceAtLeast(0L)}ms",
        )
        checkpointHandler.removeCallbacks(checkpointRunnable)
        flushProgress()
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        ioExecutor.shutdownNow()
        cacheExecutor.shutdownNow()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    private fun startResolvedPlayback(playback: PlannedPlayback, resetRetryCount: Boolean = true) {
        currentPlayback = playback
        resolveGeneration += 1L
        val generation = resolveGeneration
        if (resetRetryCount) {
            retryCountForCurrentItem = 0
        }
        currentSettings = playerSettingsStore.load()
        applyPlayerSettings(currentSettings)
        logger.info(
            TAG,
            "Resolving audio for item=${playback.plan.item.title}, bvid=${playback.plan.item.key.bvid}, cid=${playback.plan.item.key.cid}, start=${playback.plan.startPositionMs}ms",
        )
        repository.rememberSession(playback)
        ioExecutor.execute {
            val effectivePlayback = refreshPlaybackSnapshotIfNeeded(playback)
            try {
                val cachedAudioUri = repository.cachedAudioUri(effectivePlayback.plan.item)
                val audio = if (cachedAudioUri == null) {
                    repository.resolveAudio(effectivePlayback)
                } else {
                    null
                }
                val mediaUri = cachedAudioUri ?: Uri.parse(audio!!.selected.url)
                mainHandler.post {
                    if (generation != resolveGeneration) {
                        return@post
                    }
                    currentPlayback = effectivePlayback
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(effectivePlayback.plan.item.title)
                        .setArtist(effectivePlayback.plan.item.authorName)
                    effectivePlayback.plan.item.coverUrl?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }
                    val mediaItem = MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMediaId(effectivePlayback.plan.item.key.asStoreKey())
                        .setMediaMetadata(metadataBuilder.build())
                        .build()
                    if (audio == null) {
                        logger.info(TAG, "Starting playback from cached audio file.")
                    } else {
                        logger.info(
                            TAG,
                            "Starting playback with source kind=${audio.selected.kind}, streamId=${audio.selected.id}, candidates=${audio.candidates.size}",
                        )
                    }
                    startInForeground(
                        title = effectivePlayback.plan.item.title,
                        text = "正在播放",
                    )
                    player.setMediaItem(mediaItem, effectivePlayback.plan.startPositionMs)
                    player.prepare()
                    player.playWhenReady = true
                    player.play()
                    scheduleAutoCacheMaintenance(effectivePlayback)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to resolve audio", exception)
                logger.error(TAG, "Failed to resolve audio for current item.", exception)
                mainHandler.post {
                    if (generation != resolveGeneration) {
                        return@post
                    }
                    stopPlayback()
                }
            }
        }
    }

    private fun retryCurrentItem() {
        val playback = currentPlayback ?: return
        if (retryCountForCurrentItem >= MAX_RETRY_PER_ITEM) {
            Log.e(TAG, "Giving up on current item after retries: ${playback.plan.item.title}")
            logger.error(TAG, "Giving up on current item after retries: ${playback.plan.item.title}")
            stopPlayback()
            return
        }

        retryCountForCurrentItem += 1
        val retryPositionMs = player.currentPosition.coerceAtLeast(0L)
        val retryPlayback = playback.copy(
            plan = playback.plan.copy(startPositionMs = retryPositionMs),
        )
        Log.w(
            TAG,
            "Retrying current item (${retryCountForCurrentItem}/$MAX_RETRY_PER_ITEM) at ${retryPositionMs}ms",
        )
        logger.error(
            TAG,
            "Retrying current item (${retryCountForCurrentItem}/$MAX_RETRY_PER_ITEM) at ${retryPositionMs}ms",
        )
        startResolvedPlayback(retryPlayback, resetRetryCount = false)
    }

    private fun advanceToNext() {
        val playback = currentPlayback ?: return
        logger.info(TAG, "Current item ended. Advancing from index=${playback.plan.startIndex}.")
        ioExecutor.execute {
            try {
                val refreshedPlayback = refreshPlaybackSnapshotIfNeeded(playback)
                val outcome = repository.advance(refreshedPlayback)
                val nextPlayback = outcome.nextPlayback
                if (nextPlayback == null) {
                    logger.info(TAG, "Reached end of queue. Stopping playback.")
                    mainHandler.post {
                        stopPlayback()
                    }
                } else {
                    logger.info(
                        TAG,
                        "Advanced to next item index=${nextPlayback.plan.startIndex}, title=${nextPlayback.plan.item.title}",
                    )
                    startInForeground(
                        title = nextPlayback.plan.item.title,
                        text = "正在加载下一条...",
                    )
                    mainHandler.post {
                        startResolvedPlayback(nextPlayback)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to advance playback", exception)
                logger.error(TAG, "Failed to advance playback.", exception)
                mainHandler.post {
                    stopPlayback()
                }
            }
        }
    }

    private fun refreshPlaybackSnapshotIfNeeded(playback: PlannedPlayback): PlannedPlayback {
        if (!repository.isFavorited(playback)) {
            return playback
        }
        return try {
            val refreshedPlayback = repository.refreshPlaybackSnapshot(playback)
            if (
                refreshedPlayback.queue.items.size != playback.queue.items.size ||
                refreshedPlayback.plan.startIndex != playback.plan.startIndex
            ) {
                logger.info(
                    TAG,
                    "Refreshed queue snapshot: items ${playback.queue.items.size} -> ${refreshedPlayback.queue.items.size}, index ${playback.plan.startIndex} -> ${refreshedPlayback.plan.startIndex}",
                )
            }
            repository.saveLastPlayback(refreshedPlayback)
            repository.rememberSession(refreshedPlayback)
            refreshedPlayback
        } catch (exception: Exception) {
            logger.error(TAG, "Failed to refresh queue snapshot; using cached queue.", exception)
            playback
        }
    }

    private fun flushProgress() {
        val playback = currentPlayback ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        try {
            repository.saveProgress(playback, positionMs)
            logger.info(
                TAG,
                "Checkpoint saved for ${playback.plan.item.title} at ${positionMs}ms",
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to persist playback progress", exception)
            logger.error(TAG, "Failed to persist playback progress.", exception)
        }
    }

    private fun stopPlayback() {
        flushProgress()
        logger.info(TAG, "Stopping playback service.")
        currentPlayback = null
        resolveGeneration += 1L
        player.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun scheduleAutoCacheMaintenance(playback: PlannedPlayback) {
        if (playback.queue.collectionId == null) {
            return
        }
        cacheExecutor.execute {
            try {
                val result = repository.maintainAutoCache(playback)
                logger.info(
                    TAG,
                    "Auto cache maintenance: wifi=${result.wifiAvailable}, targets=${result.targetCount}, cached=${result.cachedCount}, downloaded=${result.downloadedCount}, deleted=${result.deletedCount}",
                )
            } catch (exception: Exception) {
                logger.error(TAG, "Auto cache maintenance failed.", exception)
            }
        }
    }

    private fun registerNetworkCallback() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = manager
        runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { logger.error(TAG, "Failed to register network callback.", it) }
    }

    private fun unregisterNetworkCallback() {
        val manager = connectivityManager ?: return
        runCatching { manager.unregisterNetworkCallback(networkCallback) }
            .onFailure { logger.error(TAG, "Failed to unregister network callback.", it) }
        connectivityManager = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "播放",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持 Bili Listen 的前台播放服务存活。"
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(mainActivityPendingIntent())
            .build()
        startForeground(NOTIFICATION_ID, notification)
        logger.info(TAG, "Foreground notification updated: $title / $text")
    }

    private fun applyPlayerSettings(settings: PlayerSettings) {
        player.setPlaybackParameters(PlaybackParameters(settings.speed))
        player.setRepeatMode(
            when (settings.playMode) {
                PlayMode.AUTO_NEXT -> Player.REPEAT_MODE_OFF
                PlayMode.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            },
        )
        logger.info(TAG, "Applied player settings: speed=${settings.speed}x, mode=${settings.playMode}")
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "AudioPlaybackService"
        private const val ACTION_START_PLAYBACK = "com.bililisten.android.action.START_PLAYBACK"
        private const val ACTION_SKIP_NEXT = "com.bililisten.android.action.SKIP_NEXT"
        private const val ACTION_UPDATE_PLAYBACK_CONTEXT = "com.bililisten.android.action.UPDATE_PLAYBACK_CONTEXT"
        private const val EXTRA_PLAYBACK_JSON = "playback_json"
        private const val CHECKPOINT_INTERVAL_MS = 5_000L
        private const val MAX_RETRY_PER_ITEM = 1
        private const val BILIBILI_REFERER = "https://www.bilibili.com/"
        private const val BILIBILI_USER_AGENT = "Mozilla/5.0"
        private const val NOTIFICATION_CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001

        private fun playbackStateLabel(state: Int): String = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }

        fun start(context: Context, playback: PlannedPlayback) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START_PLAYBACK
                putExtra(EXTRA_PLAYBACK_JSON, PlaybackJsonCodec.encode(playback))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun skipNext(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_SKIP_NEXT
            }
            context.startService(intent)
        }

        fun updatePlaybackContext(context: Context, playback: PlannedPlayback) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_UPDATE_PLAYBACK_CONTEXT
                putExtra(EXTRA_PLAYBACK_JSON, PlaybackJsonCodec.encode(playback))
            }
            context.startService(intent)
        }
    }
}
