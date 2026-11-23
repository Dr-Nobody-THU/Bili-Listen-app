package com.bililisten.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.bililisten.android.data.FavoriteCollection
import com.bililisten.android.data.PlannedPlayback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

internal data class RecordCollection(val id: String, val title: String, val coverUrl: String?)

internal fun recordCollectionId(playback: PlannedPlayback): String =
    playback.queue.collectionId ?: "single:${playback.plan.item.key.asStoreKey()}"

/** Freeze the collection order on entry: progress updates must never move records under the finger. */
@Stable
internal class RecordBrowseSession(playback: PlannedPlayback, favorites: List<FavoriteCollection>) {
    val originId = recordCollectionId(playback)
    val entries: List<RecordCollection>
    val pager: PagerState
    var active by mutableStateOf(true)
    var bounds = Rect.Zero
    val preview: RecordCollection get() = entry(pager.currentPage)
    val previewIndex: Int get() = Math.floorMod(pager.currentPage, entries.size)

    init {
        val current = RecordCollection(originId, playback.queue.collectionTitle ?: playback.plan.item.title, playback.plan.item.coverUrl)
        val saved = favorites.distinctBy { it.id }.map { if (it.id == originId) current else RecordCollection(it.id, it.title, it.coverUrl) }
        entries = if (saved.any { it.id == originId }) saved else listOf(current) + saved
        val index = entries.indexOfFirst { it.id == originId }
        pager = PagerState(currentPage = if (entries.size == 1) 0 else anchor(index), pageCount = {
            if (entries.size == 1) 1 else Int.MAX_VALUE
        })
    }

    fun entry(page: Int): RecordCollection = entries[Math.floorMod(page, entries.size)]
    fun anchor(index: Int): Int = (Int.MAX_VALUE / 2 / entries.size) * entries.size + index
}

/** Consume the whole outside gesture, so dismissing can never also press pause/skip/import underneath. */
internal fun Modifier.dismissRecordBrowseOutside(session: RecordBrowseSession?, rootPosition: Offset): Modifier =
    if (session?.active != true) this else pointerInput(session, rootPosition) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!session.bounds.contains(down.position + rootPosition)) {
                down.consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                session.active = false
            }
        }
    }

@Composable
internal fun RecordCollectionBrowser(session: RecordBrowseSession, progress: State<Float>, onConfirm: (String) -> Unit,
    landscape: Boolean, originRotation: () -> Float) {
    val scope = rememberCoroutineScope()
    // Rebase only at rest near an integer boundary. The visible collection and offset stay identical.
    LaunchedEffect(session) {
        snapshotFlow { session.pager.settledPage to session.pager.isScrollInProgress }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving && session.entries.size > 1 && (page < 100 || page > Int.MAX_VALUE - 100)) {
                session.pager.scrollToPage(session.anchor(Math.floorMod(page, session.entries.size)))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }, contentAlignment = Alignment.Center) {
        val discSize = recordBrowseDiscSize(maxWidth, maxHeight, landscape)
        val frameWidth = minOf(maxWidth, maxHeight * 1.08f)
        val normalDisc = frameWidth * .84f
        val centerShift = (frameWidth / 1.08f - normalDisc) / 2
        val pageExtent = discSize * .72f
        val viewportExtent = if (landscape) maxHeight else maxWidth
        val sidePadding = ((viewportExtent - pageExtent) / 2).coerceAtLeast(0.dp)
        // Use the whole artwork viewport, including the cross axis, so the larger record
        // at the beginning/end of the transition can never hit a narrow rectangular clip.
        val pagerModifier = Modifier.fillMaxSize().testTag("record_collection_browser")
                .graphicsLayer {
                    val normalScale = 1f + (discSize / normalDisc - 1f) * progress.value
                    translationY = centerShift.toPx() * normalScale * (1f - progress.value)
                }
                .recordCarouselEdgeFade(vertical = landscape)
                .onGloballyPositioned { session.bounds = it.boundsInRoot() }
                .clickable(enabled = session.active, onClickLabel = "取消切换合集", onClick = { session.active = false })
                .semantics {
                    stateDescription = "${if (landscape) "上下" else "左右"}滑动浏览收藏合集，${session.previewIndex + 1} / ${session.entries.size}"
                    collectionInfo = if (landscape) CollectionInfo(session.entries.size, 1) else CollectionInfo(1, session.entries.size)
                    dismiss { session.active = false; true }
                }
        val pageContent: @Composable PagerScope.(Int) -> Unit = { page ->
            val entry = session.entry(page)
            val centered = page == session.pager.currentPage
            // Only the nearest record needs to draw above its neighbors. Keep continuous
            // scroll offsets inside graphicsLayer so swiping does not recompose every disc.
            Box(Modifier.fillMaxSize().zIndex(if (centered) 1f else 0f), contentAlignment = Alignment.Center) {
                VinylDisc(entry.coverUrl, Modifier.requiredSize(discSize)
                .graphicsLayer {
                    val offset = ((session.pager.currentPage - page) + session.pager.currentPageOffsetFraction).coerceIn(-2f, 2f)
                    val distance = offset.absoluteValue.coerceAtMost(1f)
                    val sizeRatio = normalDisc / discSize
                    val scale = (1f - .22f * distance) * (sizeRatio + (1f - sizeRatio) * progress.value)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - .55f * distance)
                    rotationX = if (landscape) offset * 12f else 0f
                    rotationY = if (landscape) 0f else offset * -12f
                    rotationZ = if (entry.id == session.originId) originRotation() else 0f
                    cameraDistance = 16f * density
                }
                .clip(CircleShape)
                .testTag(if (centered) "record_collection_current" else "record_collection_adjacent_$page")
                .clickable(enabled = session.active, role = Role.Button, onClickLabel = if (centered) "播放此合集" else "浏览此合集") {
                    if (!session.pager.isScrollInProgress) {
                        if (centered) {
                            session.active = false
                            if (entry.id != session.originId) onConfirm(entry.id)
                        } else scope.launch { session.pager.animateScrollToPage(page) }
                    }
                }
                .semantics { contentDescription = "${entry.title}，${if (centered) "点按播放此合集" else "相邻合集"}" })
            }
        }
        val flingBehavior = PagerDefaults.flingBehavior(session.pager, snapAnimationSpec = tween(340, easing = FastOutSlowInEasing))
        if (landscape) {
            VerticalPager(
                state = session.pager, modifier = pagerModifier,
                pageSize = PageSize.Fixed(pageExtent), contentPadding = PaddingValues(vertical = sidePadding),
                pageSpacing = 6.dp, beyondViewportPageCount = 1,
                userScrollEnabled = session.active, overscrollEffect = null,
                flingBehavior = flingBehavior, pageContent = pageContent,
            )
        } else {
            HorizontalPager(
                state = session.pager, modifier = pagerModifier,
                pageSize = PageSize.Fixed(pageExtent), contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 6.dp, beyondViewportPageCount = 1,
                userScrollEnabled = session.active, overscrollEffect = null,
                flingBehavior = flingBehavior, pageContent = pageContent,
            )
        }
    }
}

internal fun recordBrowseDiscSize(width: Dp, height: Dp, landscape: Boolean): Dp =
    if (landscape) minOf(width * .86f, height * .72f) else minOf(width * .72f, height * .86f)

/** Feather only the records, leaving the actual panel color visible through either skin. */
private fun Modifier.recordCarouselEdgeFade(vertical: Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            // A smooth, wide opacity ramp reaches zero before the viewport clips its content.
            val stops = arrayOf(
                0f to Color.Transparent,
                .04f to Color.Black.copy(alpha = .15625f),
                .08f to Color.Black.copy(alpha = .5f),
                .12f to Color.Black.copy(alpha = .84375f),
                .16f to Color.Black,
                .84f to Color.Black,
                .88f to Color.Black.copy(alpha = .84375f),
                .92f to Color.Black.copy(alpha = .5f),
                .96f to Color.Black.copy(alpha = .15625f),
                1f to Color.Transparent,
            )
            val mask = if (vertical) Brush.verticalGradient(*stops, endY = size.height)
                else Brush.horizontalGradient(*stops, endX = size.width)
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }

@Composable
internal fun RecordCollectionTitle(session: RecordBrowseSession, landscape: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 10.dp)) {
        Text(if (session.entries.size > 1) "切换合集 · ${session.previewIndex + 1} / ${session.entries.size}" else "暂无其他收藏合集",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        AnimatedContent(targetState = session.pager.currentPage, label = "collection title",
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                if (landscape) {
                    (fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 * direction }) togetherWith
                        (fadeOut(tween(150)) + slideOutVertically(tween(240)) { -it / 2 * direction })
                } else {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 8 * direction }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(240)) { -it / 8 * direction })
                }
            }) { page ->
            Text(session.entry(page).title, Modifier.fillMaxWidth().testTag("record_collection_title"),
                style = if (landscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
