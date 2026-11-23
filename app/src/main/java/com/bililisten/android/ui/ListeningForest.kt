package com.bililisten.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bililisten.android.data.*
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ListeningArtwork(coverUrl: String?, playing: Boolean, listeningTimeMs: Long,
    forestState: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
    recordBrowse: RecordBrowseSession? = null,
    browseProgress: State<Float> = remember { mutableFloatStateOf(0f) },
    onBrowseCollections: (() -> Unit)? = null,
    onConfirmRecordCollection: (String) -> Unit = {},
    artworkKey: String? = null,
    landscape: Boolean = false,
) {
    // A single forest and view choice follow the app, independently of the selected collection.
    var forest by forestState
    val rotation = remember(artworkKey, coverUrl) { Animatable(0f) }
    Crossfade(forest, Modifier.fillMaxSize(), animationSpec = tween(220), label = "record and forest") { showForest ->
        if (showForest) {
            ListeningForest(listeningTimeMs, currentForestSeason(), onReturn = { forest = false })
        } else {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val frameWidth = minOf(maxWidth, maxHeight * 1.08f)
                val normalDisc = frameWidth * .84f
                val browseDisc = recordBrowseDiscSize(maxWidth, maxHeight, landscape)
                val centerShift = (frameWidth / 1.08f - normalDisc) / 2
                VinylRecord(coverUrl, playing && recordBrowse == null, Modifier
                    .graphicsLayer {
                        alpha = 1f - browseProgress.value
                        scaleX = 1f + (browseDisc / normalDisc - 1f) * browseProgress.value
                        scaleY = scaleX
                        translationY = -centerShift.toPx() * scaleX * browseProgress.value
                    }
                    .testTag("vinyl_forest_toggle")
                    .then(if (recordBrowse != null) Modifier.clearAndSetSemantics { } else Modifier)
                    .combinedClickable(enabled = recordBrowse == null, role = Role.Button, onClickLabel = "查看收听森林",
                        onLongClickLabel = if (onBrowseCollections != null) "浏览收藏合集" else null,
                        onLongClick = onBrowseCollections, onClick = { forest = true }), rotation = rotation)
                if (recordBrowse != null) RecordCollectionBrowser(recordBrowse, browseProgress, onConfirmRecordCollection, landscape) { rotation.value }
                if (maxHeight - minOf(maxWidth / 1.08f, maxHeight) >= 28.dp) {
                    Text(if (recordBrowse != null) "点中间唱片播放 · 点其他位置取消"
                        else if (onBrowseCollections != null) "轻点看森林 · 长按切换合集" else "轻点唱片 · 查看收听森林",
                        Modifier.align(Alignment.BottomCenter),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun currentForestSeason(): ForestSeason {
    var season by remember { mutableStateOf(ForestSeason.fromDate(LocalDate.now())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                season = ForestSeason.fromDate(LocalDate.now())
                delay(60_000)
            }
        }
    }
    return season
}

@Composable
internal fun ListeningForest(
    totalMs: Long,
    season: ForestSeason,
    onReturn: () -> Unit,
) {
    val growth = remember(totalMs) { ForestGrowth(totalMs) }
    val grove = remember(growth.earnedTrees) { ForestGrove.create(growth) }
    val colors = MaterialTheme.colorScheme
    val summary = listOf(ForestTreeStage.SMALL, ForestTreeStage.MEDIUM, ForestTreeStage.LARGE)
        .filter { growth.count(it) > 0 }.joinToString(" · ") { "${it.label} ${growth.count(it)}" }
        .ifEmpty { "第一株小树，正在萌芽" }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("listening_forest")
        .clickable(role = Role.Button, onClickLabel = "返回黑胶唱片", onClick = onReturn)
        .semantics { contentDescription = "收听森林，${season.label}，全应用总收听${formatListeningTime(totalMs)}，$summary" }) {
        val compact = maxHeight < 250.dp
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
            Text("收听森林", style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text(season.label, style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
        ForestCanvas(grove, growth.growingFraction, season, SHARED_FOREST_SEED,
            Modifier.fillMaxSize().padding(top = 26.dp,
                bottom = if (compact) 44.dp else 88.dp))
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
            if (!compact) Text(summary, style = MaterialTheme.typography.labelMedium, color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("总收听 ${formatForestTime(totalMs)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("距下一株 ${formatForestTime(growth.remainingMs, roundUp = true)}", style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { growth.growingFraction }, modifier = Modifier.fillMaxWidth().height(3.dp),
                color = colors.primary, trackColor = colors.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {})
            Text(if (compact) "轻点返回唱片" else "24小时一株 · 四株合成一级 · 轻点返回唱片", fontSize = 10.sp,
                lineHeight = 14.sp, color = colors.onSurfaceVariant)
        }
    }
}

private fun formatForestTime(ms: Long, roundUp: Boolean = false): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60_000 + if (roundUp && safe % 60_000 != 0L) 1 else 0
    val hours = minutes / 60
    return when {
        hours == 0L -> "${minutes}分钟"
        minutes % 60 == 0L -> "${hours}小时"
        else -> "${hours}小时${minutes % 60}分"
    }
}
