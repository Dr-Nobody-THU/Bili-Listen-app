package com.bililisten.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.bililisten.android.data.ContentKey
import com.bililisten.android.data.QueueItem
import kotlin.math.abs

/** A local draft: only the Done action writes membership keys back to the repository. */
@Composable
internal fun SpecialFavoritesOrderEditor(
    initialItems: List<QueueItem>,
    currentKey: ContentKey?,
    onCancel: () -> Unit,
    onSave: (List<ContentKey>) -> Unit,
) {
    val listState = rememberLazyListState()
    val draftKeys = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { initialItems.map { it.key.asStoreKey() }.toMutableStateList() }
    // Keep metadata separate from order; playback moving to another item cannot reset a drag.
    val metadata = remember { initialItems.associateBy { it.key.asStoreKey() } }
    val drag = remember(listState, draftKeys) { FavoriteOrderDragState(listState, draftKeys) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScrollPerSecond = with(LocalDensity.current) { 720.dp.toPx() }
    LaunchedEffect(drag.draggedKey) {
        if (drag.draggedKey == null) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (drag.draggedKey != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(.05f)
            lastFrame = frame
            val speed = drag.edgeScrollFraction(edge)
            if (speed != 0f) listState.scrollBy(speed * maxScrollPerSecond * seconds)
            drag.moveToHoveredItem()
        }
    }
    val colors = MaterialTheme.colorScheme
    val landscape = isLandscapeWindow()
    Column(
        Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp).testTag("special_order_editor"),
        verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "取消排序")
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("手动排序", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("特别收藏 · ${draftKeys.size} 条", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (landscape) Text("长按右侧三横杠，上下拖动调整顺序", Modifier.weight(1.5f),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            TextButton(onClick = { onSave(draftKeys.mapNotNull { metadata[it]?.key }) },
                modifier = Modifier.testTag("save_special_order")) {
                Text("完成", fontWeight = FontWeight.Bold)
            }
        }
        if (!landscape) Text("长按右侧三横杠，上下拖动调整顺序", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (draftKeys.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("还没有特别收藏的音频", color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("special_order_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(draftKeys, key = { _, key -> key }) { index, key ->
                    val item = metadata[key] ?: return@itemsIndexed
                    val dragging = drag.draggedKey == key
                    val haptic = LocalHapticFeedback.current
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(88.dp)
                            .testTag("special_order_item_$key")
                            .zIndex(if (dragging) 1f else 0f)
                            .then(if (dragging) Modifier else Modifier.animateItem())
                            .graphicsLayer {
                                translationY = if (dragging) drag.translationY() else 0f
                            }
                            .semantics {
                                stateDescription = "第 ${index + 1} 项，共 ${draftKeys.size} 项"
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("上移") { drag.moveKey(key, -1); true })
                                    if (index < draftKeys.lastIndex) add(CustomAccessibilityAction("下移") { drag.moveKey(key, 1); true })
                                }
                            },
                        color = if (dragging) colors.primaryContainer else colors.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        border = if (dragging) BorderStroke(1.dp, colors.primary.copy(alpha = .5f)) else null,
                        shadowElevation = if (dragging) 6.dp else 0.dp,
                    ) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.width(26.dp), style = MaterialTheme.typography.labelMedium,
                                color = if (item.key == currentKey) colors.primary else colors.onSurfaceVariant)
                            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHighest) {
                                if (item.coverUrl.isNullOrBlank()) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Headphones, null, Modifier.size(22.dp), tint = colors.primary)
                                    }
                                } else {
                                    AsyncImage(item.coverUrl, null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(listOfNotNull(if (item.key == currentKey) "正在播放" else null, item.authorName)
                                    .joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.key == currentKey) colors.primary else colors.onSurfaceVariant)
                            }
                            Box(
                                Modifier.width(48.dp).fillMaxHeight()
                                    .testTag("special_order_handle_$key")
                                    .semantics { contentDescription = "拖动排序：${item.title}" }
                                    .pointerInput(drag, key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                if (drag.start(key)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = drag::stop,
                                            onDragCancel = drag::stop,
                                        ) { change, amount ->
                                            change.consume()
                                            drag.dragBy(amount.y)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Menu, null, Modifier.size(24.dp), tint = if (dragging) colors.primary else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class FavoriteOrderDragState(
    private val list: LazyListState,
    private val keys: MutableList<String>,
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    private var top by mutableFloatStateOf(0f)
    private var height = 0

    fun start(key: String): Boolean {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        draggedKey = key
        top = item.offset.toFloat()
        height = item.size
        return true
    }

    fun stop() {
        moveToHoveredItem()
        draggedKey = null
    }

    fun dragBy(amount: Float) {
        if (draggedKey == null) return
        top += amount
        moveToHoveredItem()
    }

    fun translationY(): Float = top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }?.offset ?: 0)

    fun edgeScrollFraction(edge: Float): Float {
        val layout = list.layoutInfo
        return when {
            top < layout.viewportStartOffset + edge -> ((top - layout.viewportStartOffset - edge) / edge).coerceAtLeast(-1f)
            top + height > layout.viewportEndOffset - edge -> ((top + height - layout.viewportEndOffset + edge) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    fun moveToHoveredItem() {
        val key = draggedKey ?: return
        val from = keys.indexOf(key)
        val visible = list.layoutInfo.visibleItemsInfo
        val center = top + height / 2f
        val target = visible.filter { item ->
            val midpoint = item.offset + item.size / 2f
            (item.index > from && center >= midpoint - .5f) || (item.index < from && center <= midpoint + .5f)
        }.minByOrNull { abs(center - (it.offset + it.size / 2f)) } ?: return
        // Slots keep their geometry while the keyed rows are remeasured. Using the slot index
        // also handles a quick final movement before the previous swap has reached layout.
        if (target.index in keys.indices && target.index != from) move(from, target.index)
    }

    fun moveKey(key: String, delta: Int) {
        val from = keys.indexOf(key)
        if (from >= 0) move(from, (from + delta).coerceIn(0, keys.lastIndex))
    }

    private fun move(from: Int, to: Int) {
        // Keep the viewport fixed when moving its first item; LazyColumn otherwise follows that key.
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        keys.add(to, keys.removeAt(from))
    }
}
