package com.bililisten.android.lockscreen

import androidx.activity.compose.BackHandler
import com.bililisten.android.ui.ListenTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililisten.android.ui.CachedArtwork
import com.bililisten.android.data.AppSkin
import com.bililisten.android.ui.VinylRecord
import com.bililisten.android.ui.isLandscapeWindow

internal data class MusicLockScreenState(
    val title: String = "让声音慢下来",
    val author: String = "栖声",
    val collection: String = "",
    val episode: String = "",
    val coverUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val playRequested: Boolean = false,
    val loading: Boolean = false,
    val canControl: Boolean = false,
    val canSkip: Boolean = false,
    val error: String? = null,
    val time: String = "",
    val date: String = "",
    val preview: Boolean = false,
    val skin: AppSkin = AppSkin.MINT_NIGHT,
)

@Composable
internal fun MusicLockScreen(
    state: MusicLockScreenState,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    ListenTheme(state.skin) {
        val colors = MaterialTheme.colorScheme
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceDim)) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val compact = maxHeight < 700.dp
            var drag by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            val translation by animateFloatAsState(drag, if (dragging) snap() else tween(180), label = "lockscreen_swipe")
            val dismiss by rememberUpdatedState(onDismiss)
            Box(Modifier.fillMaxSize().testTag("music_lock_screen")
                .graphicsLayer { translationX = translation }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragCancel = { dragging = false; drag = 0f },
                        onDragEnd = {
                            dragging = false
                            if (drag >= widthPx * .26f) dismiss() else drag = 0f
                        },
                        onHorizontalDrag = { change, amount -> change.consume(); drag = (drag + amount).coerceIn(0f, widthPx) },
                    )
                }) {
                if (state.coverUrl != null) CachedArtwork(state.coverUrl, null,
                    Modifier.fillMaxSize().blur(40.dp).alpha(.18f), ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    colors.surfaceBright.copy(alpha = .9f), colors.background.copy(alpha = .65f), colors.surfaceDim.copy(alpha = .95f)))))
                if (isLandscapeWindow()) {
                    LandscapeMusicLockScreen(state, onDismiss, onToggle, onPrevious, onNext)
                } else {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()
                        .padding(horizontal = 28.dp, vertical = if (compact) 10.dp else 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.preview) "音乐锁屏 · 预览" else "栖声", color = colors.primary,
                            style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                        Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
                        Text(state.time, color = colors.onSurface, fontSize = if (compact) 46.sp else 60.sp,
                            fontWeight = FontWeight.Light, letterSpacing = (-2).sp, maxLines = 1)
                        Text(state.date, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).testTag("lockscreen_artwork_area"),
                            contentAlignment = Alignment.Center) {
                            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                VinylRecord(state.coverUrl, state.playing,
                                    Modifier.width(minOf(maxWidth, maxHeight * 1.08f, 420.dp)))
                            }
                        }
                        Text(state.title, Modifier.fillMaxWidth(), color = colors.onSurface,
                            fontSize = if (compact) 21.sp else 24.sp, lineHeight = if (compact) 28.sp else 32.sp,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(state.author, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.episode.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(listOf(state.episode, state.collection).filter(String::isNotBlank).joinToString(" · "),
                                color = colors.primary, style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = if (compact) 16.dp else 24.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(playbackTime(state.positionMs), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress = {
                                if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                            }, modifier = Modifier.weight(1f).height(8.dp).semantics { contentDescription = "节目播放进度" },
                                color = colors.primary, trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
                            Text(playbackTime(state.durationMs), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPrevious, enabled = state.canSkip, modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                                Icon(Icons.Rounded.SkipPrevious, "上一条", Modifier.size(40.dp))
                            }
                            IconButton(onClick = onToggle, enabled = state.canControl, modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                                Icon(if (state.playRequested) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (state.playRequested) "暂停" else "播放", Modifier.size(46.dp))
                            }
                            IconButton(onClick = onNext, enabled = state.canSkip, modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                                Icon(Icons.Rounded.SkipNext, "下一条", Modifier.size(40.dp))
                            }
                        }
                        val status = state.error ?: when {
                            state.loading -> "正在加载音频…"
                            state.preview && !state.canControl -> "播放节目后，锁屏亮屏时自动显示"
                            else -> ""
                        }
                        Text(status, Modifier.padding(top = 8.dp).heightIn(min = 18.dp), style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
                        Row(Modifier.fillMaxWidth().height(48.dp).background(colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button, onClickLabel = if (state.preview) "关闭预览" else "返回系统锁屏", onClick = onDismiss)
                            .semantics { contentDescription = if (state.preview) "关闭锁屏预览" else "返回系统锁屏" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
                            Text(if (state.preview) "右滑退出预览" else "右滑返回系统锁屏", Modifier.padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = colors.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeMusicLockScreen(
    state: MusicLockScreenState,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 12.dp)
        .testTag("landscape_lock_screen"), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.weight(.42f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (state.preview) "音乐锁屏 · 预览" else "栖声", style = MaterialTheme.typography.labelMedium,
                color = colors.primary, letterSpacing = 2.sp)
            Text(state.time, fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp, color = colors.onSurface)
            Text(state.date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).testTag("lockscreen_artwork_area"),
                contentAlignment = Alignment.Center) {
                VinylRecord(state.coverUrl, state.playing, Modifier.width(minOf(maxWidth, maxHeight * 1.08f, 420.dp)))
            }
        }
        Column(Modifier.weight(.58f).fillMaxHeight().testTag("lockscreen_controls")) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Text(state.title, color = colors.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.author, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.episode.isNotBlank()) Text(listOf(state.episode, state.collection).filter(String::isNotBlank).joinToString(" · "),
                    color = colors.primary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = state.error ?: when {
                    state.loading -> "正在加载音频…"
                    state.preview && !state.canControl -> "播放节目后，锁屏亮屏时自动显示"
                    else -> null
                }
                if (status != null) Text(status, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(playbackTime(state.positionMs), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = {
                    if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                }, modifier = Modifier.weight(1f).height(8.dp).semantics { contentDescription = "节目播放进度" },
                    color = colors.primary, trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
                Text(playbackTime(state.durationMs), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = state.canSkip, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                    Icon(Icons.Rounded.SkipPrevious, "上一条", Modifier.size(40.dp))
                }
                IconButton(onClick = onToggle, enabled = state.canControl, modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                    Icon(if (state.playRequested) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.playRequested) "暂停" else "播放", Modifier.size(46.dp))
                }
                IconButton(onClick = onNext, enabled = state.canSkip, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = colors.outline)) {
                    Icon(Icons.Rounded.SkipNext, "下一条", Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(48.dp).background(colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = if (state.preview) "关闭锁屏预览" else "返回系统锁屏" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
                Text(if (state.preview) "右滑退出预览" else "右滑返回系统锁屏", Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = colors.primary)
            }
        }
    }
}

private fun playbackTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1_000
    return if (seconds >= 3_600) "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
        else "%02d:%02d".format(seconds / 60, seconds % 60)
}
