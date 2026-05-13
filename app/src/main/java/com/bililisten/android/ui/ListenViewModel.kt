package com.bililisten.android.ui

import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.CollectionPlaybackOrder
import com.bililisten.android.data.CreatorCollectionSummary
import com.bililisten.android.data.DebugSnapshot
import com.bililisten.android.data.FavoriteCollection
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.PlayerSettings
import com.bililisten.android.data.ResumePolicy
import com.bililisten.android.data.StartPlan
import com.bililisten.android.player.AudioPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class PlaybackControlState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasActiveItem: Boolean = false,
)

data class ListenUiState(
    val input: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val plannedPlayback: PlannedPlayback? = null,
    val favorites: List<FavoriteCollection> = emptyList(),
    val currentFavoriteId: String? = null,
    val creatorCollections: List<CreatorCollectionSummary> = emptyList(),
    val creatorCollectionsAuthorName: String? = null,
    val isLoadingCreatorCollections: Boolean = false,
    val currentCollectionPlaybackOrder: CollectionPlaybackOrder = CollectionPlaybackOrder.COLLECTION_DEFAULT,
    val debugSnapshot: DebugSnapshot? = null,
    val playbackControl: PlaybackControlState = PlaybackControlState(),
    val playerSettings: PlayerSettings = PlayerSettings(),
)

class ListenViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = AppGraph.repository(application)
    private val logger = AppGraph.logger(application)
    private val playerSettingsStore = AppGraph.playerSettings(application)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var autoRestoreGeneration = 0L

    private val controllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishPlaybackControlState()
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            publishPlaybackControlState()
            mainHandler.postDelayed(this, 500L)
        }
    }

    var uiState by mutableStateOf(
        ListenUiState(
            playerSettings = playerSettingsStore.load(),
        ),
    )
        private set

    init {
        refreshDiagnostics()
        refreshFavorites()
        connectController()
        restoreLastPlaybackIfAvailable()
        mainHandler.post(progressTicker)
    }

    fun setInput(value: String) {
        invalidatePendingRestore()
        uiState = uiState.copy(input = value, error = null, message = null)
    }

    fun importInput(value: String, autoStart: Boolean) {
        invalidatePendingRestore()
        logger.info("ListenViewModel", "Imported input from intent: ${value.take(120)}")
        uiState = uiState.copy(input = value, error = null, message = "已导入链接")
        if (autoStart) {
            startPlayback(ResumePolicy.PREFER_REQUESTED_ITEM)
        }
    }

    fun refreshDiagnostics() {
        uiState = uiState.copy(debugSnapshot = logger.snapshot())
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null || controller.currentMediaItem == null) {
            val playback = uiState.plannedPlayback ?: return
            val snapshot = snapshotCurrentPlayback(playback)
            logger.info("ListenViewModel", "Play requested from prepared playback.")
            repository.clearCollectionProgress(snapshot.queue, keepKey = snapshot.plan.item.key)
            startPreparedPlayback(snapshot)
            uiState = uiState.copy(
                plannedPlayback = snapshot,
                playbackControl = previewPlaybackControl(snapshot),
            )
            refreshFavorites(snapshot)
            return
        }
        if (controller.isPlaying) {
            logger.info("ListenViewModel", "Pause requested from UI.")
            controller.pause()
        } else {
            logger.info("ListenViewModel", "Play requested from UI.")
            controller.play()
        }
        publishPlaybackControlState()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        logger.info("ListenViewModel", "Seek requested to ${positionMs}ms.")
        controller.seekTo(positionMs.coerceAtLeast(0L))
        publishPlaybackControlState()
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController
        if (controller != null && controller.currentMediaItem != null) {
            val durationMs = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: Long.MAX_VALUE
            val targetPosition = (controller.currentPosition + deltaMs).coerceAtLeast(0L).coerceAtMost(durationMs)
            logger.info("ListenViewModel", "Seek-by requested: delta=${deltaMs}ms, target=${targetPosition}ms.")
            controller.seekTo(targetPosition)
            publishPlaybackControlState()
            return
        }

        val playback = uiState.plannedPlayback ?: return
        val durationMs = playback.plan.item.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val targetPosition = (playback.plan.startPositionMs + deltaMs).coerceAtLeast(0L).coerceAtMost(durationMs)
        val updatedPlayback = playback.copy(
            plan = playback.plan.copy(startPositionMs = targetPosition),
        )
        logger.info("ListenViewModel", "Seek-by requested on prepared playback: delta=${deltaMs}ms, target=${targetPosition}ms.")
        uiState = uiState.copy(
            plannedPlayback = updatedPlayback,
            playbackControl = previewPlaybackControl(updatedPlayback),
        )
    }

    fun skipNext() {
        val playback = uiState.plannedPlayback ?: return
        val currentIndex = resolveCurrentQueueIndex(playback)
        val nextIndex = repository.nextIndex(playback.queue, currentIndex)
        logger.info("ListenViewModel", "Skip-next requested from UI. $currentIndex -> $nextIndex")
        playQueueIndex(nextIndex, reason = "next item")
    }

    fun skipPrevious() {
        val playback = uiState.plannedPlayback ?: return
        val currentIndex = resolveCurrentQueueIndex(playback)
        val previousIndex = repository.previousIndex(playback.queue, currentIndex)
        logger.info("ListenViewModel", "Skip-previous requested from UI. $currentIndex -> $previousIndex")
        playQueueIndex(previousIndex, reason = "previous item")
    }

    fun playQueueIndex(index: Int, reason: String = "manual selection") {
        invalidatePendingRestore()
        val playback = uiState.plannedPlayback ?: return
        val currentIndex = resolveCurrentQueueIndex(playback)
        if (index == currentIndex && mediaController?.currentMediaItem != null) {
            logger.info("ListenViewModel", "Queue item $index is already active; ignoring selection.")
            uiState = uiState.copy(message = "当前已在播放该视频")
            return
        }

        val resumeSavedProgress = index == currentIndex
        val nextPlayback = repository.planPlaybackFromQueue(
            queue = playback.queue,
            targetIndex = index,
            reason = reason,
            resumeSavedProgress = resumeSavedProgress,
        )
        if (resumeSavedProgress) {
            repository.clearCollectionProgress(nextPlayback.queue, keepKey = nextPlayback.plan.item.key)
        } else {
            repository.clearCollectionProgress(nextPlayback.queue)
        }
        startPreparedPlayback(nextPlayback)
        uiState = uiState.copy(
            plannedPlayback = nextPlayback,
            message = "已切换播放项",
            playbackControl = previewPlaybackControl(nextPlayback),
        )
        refreshFavorites(nextPlayback)
        refreshDiagnostics()
    }

    fun updatePlaybackSpeed(speed: Float) {
        val updatedSettings = playerSettingsStore.saveSpeed(speed)
        logger.info("ListenViewModel", "Playback speed changed to ${updatedSettings.speed}x.")
        uiState = uiState.copy(playerSettings = updatedSettings)
        applySettingsToController(updatedSettings)
    }

    fun updatePlayMode(playMode: PlayMode) {
        val updatedSettings = playerSettingsStore.savePlayMode(playMode)
        logger.info("ListenViewModel", "Play mode changed to ${updatedSettings.playMode}.")
        uiState = uiState.copy(playerSettings = updatedSettings)
        applySettingsToController(updatedSettings)
    }

    fun updateCollectionPlaybackOrder(order: CollectionPlaybackOrder) {
        val playback = uiState.plannedPlayback ?: return
        val snapshot = snapshotCurrentPlayback(playback)
        logger.info("ListenViewModel", "Collection playback order changed to $order.")
        val updatedPlayback = repository.updateCollectionPlaybackOrder(
            playback = snapshot,
            order = order,
            preservePositionMs = snapshot.plan.startPositionMs,
        )
        uiState = uiState.copy(
            plannedPlayback = updatedPlayback,
            playbackControl = if (uiState.playbackControl.mediaId == snapshot.plan.item.key.asStoreKey()) {
                uiState.playbackControl.copy(positionMs = snapshot.plan.startPositionMs)
            } else {
                previewPlaybackControl(updatedPlayback)
            },
            currentCollectionPlaybackOrder = order,
        )
        if (mediaController?.currentMediaItem != null) {
            AudioPlaybackService.updatePlaybackContext(getApplication(), updatedPlayback)
        }
        refreshFavorites(updatedPlayback)
    }

    fun startPlayback(policy: ResumePolicy) {
        invalidatePendingRestore()
        val input = uiState.input.trim()
        if (input.isEmpty()) {
            logger.error("ListenViewModel", "User attempted to start playback without input.")
            uiState = uiState.copy(error = "请先输入 BV 号或 B 站链接。", message = null)
            return
        }

        uiState = uiState.copy(input = "", isLoading = true, error = null, message = null)
        executor.execute {
            try {
                logger.info("ListenViewModel", "Planning playback with policy=$policy for ${input.take(120)}")
                val playback = repository.planPlayback(input, policy)
                repository.clearCollectionProgress(playback.queue)
                mainHandler.post {
                    startPreparedPlayback(playback)
                    uiState = uiState.copy(
                        isLoading = false,
                        message = "开始播放",
                        error = null,
                        plannedPlayback = playback,
                        playbackControl = previewPlaybackControl(playback),
                        debugSnapshot = logger.snapshot(),
                    )
                    refreshFavorites(playback)
                }
            } catch (exception: Exception) {
                logger.error("ListenViewModel", "Failed to resolve playback input.", exception)
                mainHandler.post {
                    uiState = uiState.copy(
                        isLoading = false,
                        message = null,
                        error = exception.message ?: "解析这个链接失败。",
                        debugSnapshot = logger.snapshot(),
                    )
                }
            }
        }
    }

    fun toggleCurrentFavorite() {
        val playback = uiState.plannedPlayback ?: return
        val snapshot = snapshotCurrentPlayback(playback)
        val favorited = repository.toggleFavorite(snapshot)
        uiState = uiState.copy(
            plannedPlayback = snapshot,
            playbackControl = previewPlaybackControl(snapshot),
            message = if (favorited) "已加入收藏" else "已取消收藏",
            error = null,
        )
        refreshFavorites(snapshot)
    }

    fun openFavorite(favoriteId: String) {
        invalidatePendingRestore()
        val favorite = repository.loadFavorite(favoriteId) ?: run {
            uiState = uiState.copy(message = null, error = "找不到这个收藏。")
            refreshFavorites()
            return
        }
        executor.execute {
            val playback = runCatching { repository.refreshPlaybackSnapshot(favorite.playback) }
                .onFailure { logger.error("ListenViewModel", "Failed to refresh favorite snapshot before opening.", it) }
                .getOrDefault(favorite.playback)
            mainHandler.post {
                logger.info("ListenViewModel", "Opening favorite playback: $favoriteId")
                startPreparedPlayback(playback)
                uiState = uiState.copy(
                    plannedPlayback = playback,
                    message = "已切换到收藏合集",
                    error = null,
                    playbackControl = previewPlaybackControl(playback),
                )
                refreshFavorites(playback)
            }
        }
    }

    fun loadCreatorCollectionsForCurrentAuthor() {
        invalidatePendingRestore()
        val item = uiState.plannedPlayback?.plan?.item ?: run {
            uiState = uiState.copy(message = null, error = "No active author information.")
            return
        }
        val authorMid = item.authorMid ?: run {
            uiState = uiState.copy(message = null, error = "This video does not expose an author id.")
            return
        }
        uiState = uiState.copy(
            isLoadingCreatorCollections = true,
            creatorCollections = emptyList(),
            creatorCollectionsAuthorName = item.authorName,
            error = null,
            message = null,
        )
        executor.execute {
            try {
                logger.info("ListenViewModel", "Loading creator collections for mid=$authorMid")
                val creatorCollections = repository.listCreatorCollections(
                    authorMid = authorMid,
                    authorName = item.authorName,
                    authorAvatarUrl = item.authorAvatarUrl,
                )
                mainHandler.post {
                    uiState = uiState.copy(
                        isLoadingCreatorCollections = false,
                        creatorCollections = creatorCollections,
                        creatorCollectionsAuthorName = item.authorName,
                        error = null,
                    )
                }
            } catch (exception: Exception) {
                logger.error("ListenViewModel", "Failed to load creator collections.", exception)
                mainHandler.post {
                    uiState = uiState.copy(
                        isLoadingCreatorCollections = false,
                        creatorCollections = emptyList(),
                        creatorCollectionsAuthorName = item.authorName,
                        error = exception.message ?: "Failed to load creator collections.",
                    )
                }
            }
        }
    }

    fun openCreatorCollection(summary: CreatorCollectionSummary) {
        invalidatePendingRestore()
        uiState = uiState.copy(isLoading = true, error = null, message = null)
        executor.execute {
            try {
                logger.info(
                    "ListenViewModel",
                    "Opening creator collection ${summary.id} with leadBvid=${summary.leadBvid ?: "none"}",
                )
                val playback = repository.planPlaybackFromCreatorCollection(summary)
                repository.clearCollectionProgress(playback.queue)
                mainHandler.post {
                    startPreparedPlayback(playback)
                    uiState = uiState.copy(
                        isLoading = false,
                        plannedPlayback = playback,
                        playbackControl = previewPlaybackControl(playback),
                        debugSnapshot = logger.snapshot(),
                        error = null,
                    )
                    refreshFavorites(playback)
                }
            } catch (exception: Exception) {
                logger.error("ListenViewModel", "Failed to open creator collection.", exception)
                mainHandler.post {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to open creator collection.",
                    )
                }
            }
        }
    }

    private fun connectController() {
        val application = getApplication<Application>()
        val sessionToken = SessionToken(
            application,
            ComponentName(application, AudioPlaybackService::class.java),
        )
        val future = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(controllerListener)
                    applySettingsToController(uiState.playerSettings)
                    logger.info("ListenViewModel", "MediaController connected.")
                    publishPlaybackControlState()
                } catch (exception: Exception) {
                    logger.error("ListenViewModel", "Failed to connect MediaController.", exception)
                    publishPlaybackControlState()
                }
            },
            application.mainExecutor,
        )
    }

    private fun restoreLastPlaybackIfAvailable() {
        val lastPlayback = repository.loadLastPlayback() ?: return
        val generation = autoRestoreGeneration
        executor.execute {
            try {
                logger.info("ListenViewModel", "Restoring last playback from saved playback state.")
                val restoredPlayback = runCatching { repository.refreshPlaybackSnapshot(lastPlayback) }
                    .onFailure { logger.error("ListenViewModel", "Failed to refresh saved playback snapshot.", it) }
                    .getOrDefault(lastPlayback)
                mainHandler.post {
                    if (generation != autoRestoreGeneration) {
                        return@post
                    }
                    uiState = uiState.copy(
                        plannedPlayback = restoredPlayback,
                        playbackControl = previewPlaybackControl(restoredPlayback),
                        message = "已恢复上次播放",
                        error = null,
                        debugSnapshot = logger.snapshot(),
                    )
                    refreshFavorites(restoredPlayback)
                }
            } catch (exception: Exception) {
                logger.error("ListenViewModel", "Failed to restore last playback.", exception)
            }
        }
    }

    private fun startPreparedPlayback(playback: PlannedPlayback) {
        repository.rememberSession(playback)
        repository.saveLastPlayback(playback)
        AudioPlaybackService.start(getApplication(), playback)
    }

    private fun invalidatePendingRestore() {
        autoRestoreGeneration += 1L
    }

    private fun applySettingsToController(settings: PlayerSettings) {
        val controller = mediaController ?: return
        controller.setPlaybackParameters(PlaybackParameters(settings.speed))
        controller.setRepeatMode(
            when (settings.playMode) {
                PlayMode.AUTO_NEXT -> Player.REPEAT_MODE_OFF
                PlayMode.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            },
        )
        publishPlaybackControlState()
    }

    private fun publishPlaybackControlState() {
        val controller = mediaController
        val preparedPlayback = uiState.plannedPlayback
        if (controller == null) {
            uiState = uiState.copy(playbackControl = buildPreparedPlaybackControl(preparedPlayback, connected = false))
            return
        }

        if (controller.currentMediaItem == null) {
            uiState = uiState.copy(playbackControl = buildPreparedPlaybackControl(preparedPlayback, connected = true))
            return
        }

        val durationMs = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        uiState = uiState.copy(
            playbackControl = PlaybackControlState(
                connected = true,
                mediaId = controller.currentMediaItem?.mediaId,
                title = controller.mediaMetadata.title?.toString(),
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = durationMs,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                hasActiveItem = controller.currentMediaItem != null,
            ),
        )
    }

    private fun previewPlaybackControl(playback: PlannedPlayback): PlaybackControlState {
        return uiState.playbackControl.copy(
            connected = uiState.playbackControl.connected,
            mediaId = playback.plan.item.key.asStoreKey(),
            title = playback.plan.item.title,
            positionMs = playback.plan.startPositionMs,
            durationMs = playback.plan.item.durationMs,
            hasActiveItem = true,
        )
    }

    private fun resolveCurrentQueueIndex(playback: PlannedPlayback): Int {
        val currentMediaId = uiState.playbackControl.mediaId
        if (currentMediaId != null) {
            val matchedIndex = playback.queue.items.indexOfFirst { it.key.asStoreKey() == currentMediaId }
            if (matchedIndex >= 0) {
                return matchedIndex
            }
        }
        return playback.plan.startIndex
    }

    private fun snapshotCurrentPlayback(playback: PlannedPlayback): PlannedPlayback {
        val currentIndex = resolveCurrentQueueIndex(playback)
        val item = playback.queue.items[currentIndex]
        val positionMs = if (uiState.playbackControl.mediaId == item.key.asStoreKey()) {
            uiState.playbackControl.positionMs
        } else {
            playback.plan.startPositionMs
        }.coerceAtLeast(0L)
        return playback.copy(
            plan = StartPlan(
                startIndex = currentIndex,
                item = item,
                startPositionMs = positionMs,
                reason = playback.plan.reason,
            ),
        )
    }

    private fun buildPreparedPlaybackControl(playback: PlannedPlayback?, connected: Boolean): PlaybackControlState {
        if (playback == null) {
            return PlaybackControlState(connected = connected)
        }
        return PlaybackControlState(
            connected = connected,
            mediaId = playback.plan.item.key.asStoreKey(),
            title = playback.plan.item.title,
            positionMs = playback.plan.startPositionMs,
            durationMs = playback.plan.item.durationMs,
            isPlaying = false,
            isBuffering = false,
            hasActiveItem = true,
        )
    }

    private fun refreshFavorites(playback: PlannedPlayback? = uiState.plannedPlayback) {
        val favorites = repository.listFavorites()
        val currentFavoriteId = playback
            ?.let(repository::favoriteIdOf)
            ?.takeIf { id -> favorites.any { it.id == id } }
        val currentCollectionPlaybackOrder = repository.loadCollectionPlaybackOrder(playback?.queue?.collectionId)
        uiState = uiState.copy(
            favorites = favorites,
            currentFavoriteId = currentFavoriteId,
            currentCollectionPlaybackOrder = currentCollectionPlaybackOrder,
        )
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(progressTicker)
        mediaController?.removeListener(controllerListener)
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        executor.shutdownNow()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ListenViewModel(application) as T
            }
        }
    }
}
