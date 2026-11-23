package com.bililisten.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.AppSkin
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlayerSettings

private enum class SettingsPage { ROOT, BACKGROUND, LOCK_SCREEN }

/** One dialog owns the whole settings flow. Only back navigation returns to its root page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSettingsSheet(
    settings: PlayerSettings,
    onDismiss: () -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onPlayModeChanged: (PlayMode) -> Unit,
    onAllowMobileStreamingChanged: (Boolean) -> Unit,
    onFadePlaybackChanged: (Boolean) -> Unit,
    onBackgroundAudioChanged: (BackgroundAudioSettings) -> Unit,
    onMusicLockScreenChanged: (Boolean) -> Unit,
    onSkinChanged: (AppSkin) -> Unit,
    onRecordCollectionSwitchChanged: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    val pageState = rememberSaveableStateHolder()
    val goBack = { page = SettingsPage.ROOT }
    StableBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playback_settings_sheet")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        sheetMaxWidth = if (isLandscapeWindow()) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.fillMaxWidth().testTag("settings_sheet_drag_handle"), contentAlignment = Alignment.Center) {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
            }
        },
    ) {
        // Register inside the dialog, ahead of its default dismiss callback in dispatch order.
        BackHandler(enabled = page != SettingsPage.ROOT, onBack = goBack)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 740.dp)) {
            // All pages share a stable height, so horizontal navigation never reopens the sheet.
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth().height(maxHeight).clipToBounds(),
                transitionSpec = {
                    val forward = targetState != SettingsPage.ROOT
                    slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (forward) it else -it } togetherWith
                        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (forward) -it else it }
                },
                label = "playback settings navigation",
            ) { currentPage ->
                pageState.SaveableStateProvider(currentPage.name) {
                    val backGesture = if (currentPage == SettingsPage.ROOT) Modifier else Modifier.pointerInput(currentPage) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onDragCancel = { distance = 0f },
                            onDragEnd = { if (distance > size.width * .24f) goBack() },
                        ) { _, amount -> distance = (distance + amount).coerceAtLeast(0f) }
                    }
                    Box(Modifier.fillMaxSize().testTag("settings_page_${currentPage.name.lowercase()}").then(backGesture)) {
                        when (currentPage) {
                            SettingsPage.ROOT -> PlaybackSettingsContent(
                                settings, onPlaybackSpeedChanged, onPlayModeChanged,
                                onAllowMobileStreamingChanged, onFadePlaybackChanged,
                                onBackgroundAudioChanged, { page = SettingsPage.BACKGROUND },
                                onMusicLockScreenChanged, { page = SettingsPage.LOCK_SCREEN },
                                onSkinChanged,
                                onRecordCollectionSwitchChanged,
                            )
                            SettingsPage.BACKGROUND -> BackgroundSoundSettingsContent(settings.backgroundAudio, onBackgroundAudioChanged, goBack)
                            SettingsPage.LOCK_SCREEN -> MusicLockScreenSettingsContent(settings.musicLockScreen, onMusicLockScreenChanged, goBack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackSettingsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回播放设置")
    }
}
