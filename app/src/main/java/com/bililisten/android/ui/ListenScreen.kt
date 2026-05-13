package com.bililisten.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.bililisten.android.data.CollectionPlaybackOrder
import com.bililisten.android.data.CreatorCollectionSummary
import com.bililisten.android.data.FavoriteCollection
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.PlayerSettings
import com.bililisten.android.data.PlayerSettingsStore
import com.bililisten.android.data.QueueItem
import com.bililisten.android.data.ResumePolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private val PrimaryTextColor = Color(0xFFF7F2ED)
private val SecondaryTextColor = Color(0xFFE2D6CC)
private val AccentColor = Color(0xFFF1E8DD)
private val PanelColor = Color(0x4D1B120E)
private val SoftPanelColor = Color(0x332C1D17)
private val FavoriteInactiveBackground = Color(0xFFF4EEE6)
private val FavoriteActiveBackground = Color(0xFFB9B0A7)
private val TopControlHeight = 62.dp
private val TopSideButtonWidth = 82.dp

private val ListenColorScheme = darkColorScheme(
    primary = Color(0xFFF2F0EA),
    onPrimary = Color(0xFF1A130F),
    secondary = Color(0xFFC0A287),
    onSecondary = Color(0xFF24140B),
    tertiary = AccentColor,
    background = Color(0xFF120B08),
    onBackground = Color(0xFFF7F2ED),
    surface = Color(0xFF1D120E),
    onSurface = Color(0xFFF7F2ED),
    surfaceVariant = Color(0xFF2A1C17),
    onSurfaceVariant = Color(0xFFD3C1B5),
    outline = Color(0xFF7B6255),
)

@Composable
fun ListenScreen(
    state: ListenUiState,
    onInputChanged: (String) -> Unit,
    onPlayRequested: (ResumePolicy) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onPlayModeChanged: (PlayMode) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFavorite: (String) -> Unit,
    onOpenCreatorCollections: () -> Unit,
    onOpenCreatorCollection: (CreatorCollectionSummary) -> Unit,
    onCollectionPlaybackOrderChanged: (CollectionPlaybackOrder) -> Unit,
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showTitleSheet by rememberSaveable { mutableStateOf(false) }
    var showFavoritesDrawer by rememberSaveable { mutableStateOf(false) }
    var showCreatorDrawer by rememberSaveable { mutableStateOf(false) }
    var inputExpanded by rememberSaveable { mutableStateOf(false) }
    val display = resolveCurrentDisplay(state.plannedPlayback, state.playbackControl.mediaId)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.error) {
        if (state.error != null) {
            inputExpanded = true
        }
    }
    LaunchedEffect(state.plannedPlayback != null) {
        if (state.plannedPlayback != null) {
            inputExpanded = false
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                inputExpanded = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    MaterialTheme(colorScheme = ListenColorScheme) {
        TitleDetailSheet(
            visible = showTitleSheet && display != null,
            item = display?.item,
            onDismiss = { showTitleSheet = false },
        )
        SettingsSheet(
            visible = showSettingsSheet,
            settings = state.playerSettings,
            onDismiss = { showSettingsSheet = false },
            onPlaybackSpeedChanged = onPlaybackSpeedChanged,
            onPlayModeChanged = onPlayModeChanged,
        )
        QueueSheet(
            visible = showQueueSheet && state.plannedPlayback != null && display != null,
            playback = state.plannedPlayback,
            activeIndex = display?.activeIndex ?: 0,
            currentOrder = state.currentCollectionPlaybackOrder,
            onDismiss = { showQueueSheet = false },
            onPlayQueueIndex = { index ->
                onPlayQueueIndex(index)
            },
            onCollectionPlaybackOrderChanged = onCollectionPlaybackOrderChanged,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2E1A14), Color(0xFF160E0B), Color(0xFF0C0807)),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TopInputBar(
                        modifier = Modifier.weight(1f),
                        input = state.input,
                        expanded = inputExpanded,
                        error = state.error,
                        onExpandedChange = { inputExpanded = it },
                        onInputChanged = onInputChanged,
                    )
                    if (inputExpanded) {
                        InputConfirmButton(
                            isLoading = state.isLoading,
                            onClick = {
                                inputExpanded = false
                                onPlayRequested(ResumePolicy.PREFER_REQUESTED_ITEM)
                            },
                        )
                    } else {
                        FavoriteDrawerButton(
                            hasFavorites = state.favorites.isNotEmpty(),
                            onClick = {
                                showTitleSheet = false
                                showSettingsSheet = false
                                showQueueSheet = false
                                showCreatorDrawer = false
                                showFavoritesDrawer = true
                            },
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (state.plannedPlayback == null || display == null) {
                        EmptyPlayerCard()
                    } else {
                        PlayerScene(
                            playback = state.plannedPlayback,
                            display = display,
                            playbackControl = state.playbackControl,
                            isFavorited = state.currentFavoriteId != null,
                            onToggleFavorite = onToggleFavorite,
                            onOpenCreatorCollections = {
                                inputExpanded = false
                                showTitleSheet = false
                                showSettingsSheet = false
                                showQueueSheet = false
                                showFavoritesDrawer = false
                                showCreatorDrawer = true
                                onOpenCreatorCollections()
                            },
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                            onSeekBy = onSeekBy,
                            onSkipPrevious = onSkipPrevious,
                            onSkipNext = onSkipNext,
                            onOpenTitleDetails = {
                                inputExpanded = false
                                showSettingsSheet = false
                                showQueueSheet = false
                                showFavoritesDrawer = false
                                showCreatorDrawer = false
                                showTitleSheet = true
                            },
                            onOpenSettings = {
                                inputExpanded = false
                                showTitleSheet = false
                                showQueueSheet = false
                                showFavoritesDrawer = false
                                showCreatorDrawer = false
                                showSettingsSheet = true
                            },
                            onOpenQueue = {
                                inputExpanded = false
                                showTitleSheet = false
                                showSettingsSheet = false
                                showFavoritesDrawer = false
                                showCreatorDrawer = false
                                showQueueSheet = true
                            },
                        )
                    }
                    if (inputExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { inputExpanded = false },
                        )
                    }
                }
            }
            FavoriteDrawer(
                visible = showFavoritesDrawer,
                favorites = state.favorites,
                activeFavoriteId = state.currentFavoriteId,
                onDismiss = { showFavoritesDrawer = false },
                onOpenFavorite = { favoriteId ->
                    onOpenFavorite(favoriteId)
                    showFavoritesDrawer = false
                },
            )
            CreatorCollectionDrawer(
                visible = showCreatorDrawer,
                authorName = state.creatorCollectionsAuthorName,
                creatorCollections = state.creatorCollections,
                isLoading = state.isLoadingCreatorCollections,
                activeCollectionId = state.plannedPlayback?.queue?.collectionId,
                onDismiss = { showCreatorDrawer = false },
                onOpenCreatorCollection = { summary ->
                    onOpenCreatorCollection(summary)
                    showCreatorDrawer = false
                },
            )
        }
    }
}

@Composable
private fun TopInputBar(
    modifier: Modifier = Modifier,
    input: String,
    expanded: Boolean,
    error: String?,
    onExpandedChange: (Boolean) -> Unit,
    onInputChanged: (String) -> Unit,
) {
    Card(
        modifier = modifier.height(TopControlHeight),
        colors = CardDefaults.cardColors(containerColor = Color(0x661F1511), contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.size(10.dp).background(AccentColor, CircleShape))
                Text(
                    text = if (input.isBlank()) "导入 BV 或 B 站链接" else input,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开输入栏", tint = SecondaryTextColor, modifier = Modifier.size(24.dp))
            }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxSize(),
                label = {
                    Text(
                        text = error ?: "BV 号或链接",
                        color = if (error != null) Color(0xFFFFD1CF) else SecondaryTextColor,
                    )
                },
                placeholder = { Text("https://www.bilibili.com/video/BV...") },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }
}

@Composable
private fun InputConfirmButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.width(TopSideButtonWidth).height(TopControlHeight),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(
            text = if (isLoading) "解析中" else "确定",
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FavoriteDrawerButton(
    hasFavorites: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(TopSideButtonWidth).height(TopControlHeight).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (hasFavorites) Color(0xFFF1E8DD) else Color(0x332B1D17),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("收藏夹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (hasFavorites) Color(0xFF1A130F) else PrimaryTextColor)
            Text(if (hasFavorites) "查看续播" else "暂无收藏", style = MaterialTheme.typography.bodySmall, color = if (hasFavorites) Color(0xFF47342B) else SecondaryTextColor, maxLines = 1)
        }
    }
}

@Composable
private fun FavoriteDrawer(
    visible: Boolean,
    favorites: List<FavoriteCollection>,
    activeFavoriteId: String?,
    onDismiss: () -> Unit,
    onOpenFavorite: (String) -> Unit,
) {
    if (!visible) return

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(320.dp),
            color = Color(0xFF160F0C),
            tonalElevation = 10.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("我的收藏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
                if (favorites.isEmpty()) {
                    Surface(shape = RoundedCornerShape(24.dp), color = SoftPanelColor) {
                        Text(
                            text = "还没有收藏合集。\n先在下方点“加入收藏”。",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryTextColor,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites, key = { it.id }) { favorite ->
                            FavoriteDrawerRow(
                                favorite = favorite,
                                isActive = favorite.id == activeFavoriteId,
                                onClick = { onOpenFavorite(favorite.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDrawerRow(
    favorite: FavoriteCollection,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isActive) Color(0x4DE0C7B2) else Color(0x332B1D17),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.width(108.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF231815)) {
                if (favorite.coverUrl != null) {
                    AsyncImage(
                        model = favorite.coverUrl,
                        contentDescription = favorite.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF3B2A23)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无封面", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(favorite.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = PrimaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("共 ${favorite.itemCount} 个内容", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
            }
        }
    }
}

@Composable
private fun CreatorCollectionDrawer(
    visible: Boolean,
    authorName: String?,
    creatorCollections: List<CreatorCollectionSummary>,
    isLoading: Boolean,
    activeCollectionId: String?,
    onDismiss: () -> Unit,
    onOpenCreatorCollection: (CreatorCollectionSummary) -> Unit,
) {
    if (!visible) return

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(320.dp),
            color = Color(0xFF160F0C),
            tonalElevation = 10.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = authorName?.let { "$it 的合集" } ?: "UP 合集",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextColor,
                )
                when {
                    isLoading -> {
                        Surface(shape = RoundedCornerShape(24.dp), color = SoftPanelColor) {
                            Text(
                                text = "正在加载合集...",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryTextColor,
                            )
                        }
                    }

                    creatorCollections.isEmpty() -> {
                        Surface(shape = RoundedCornerShape(24.dp), color = SoftPanelColor) {
                            Text(
                                text = "这个 UP 暂时没有可直接播放的合集。",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryTextColor,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(creatorCollections, key = { it.id }) { summary ->
                                CreatorCollectionDrawerRow(
                                    summary = summary,
                                    isActive = summary.id == activeCollectionId,
                                    onClick = { onOpenCreatorCollection(summary) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorCollectionDrawerRow(
    summary: CreatorCollectionSummary,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isActive) Color(0x4DE0C7B2) else Color(0x332B1D17),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.width(108.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF231815),
            ) {
                if (summary.coverUrl != null) {
                    AsyncImage(
                        model = summary.coverUrl,
                        contentDescription = summary.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0x332B1D17)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("无封面", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "共 ${summary.itemCount} 个内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryTextColor,
                )
            }
        }
    }
}

@Composable
private fun EmptyPlayerCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x4D201612), contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0x332D211B), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("封面会显示在这里", style = MaterialTheme.typography.titleMedium, color = SecondaryTextColor)
            }
            Text("先导入一个合集链接，再进入听视频页面。", style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerScene(
    playback: PlannedPlayback,
    display: CurrentDisplay,
    playbackControl: PlaybackControlState,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenCreatorCollections: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenTitleDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val activeItem = display.item
    var isDragging by remember(playbackControl.mediaId) { mutableStateOf(false) }
    var dragPositionMs by remember(playbackControl.mediaId) { mutableFloatStateOf(playbackControl.positionMs.toFloat()) }

    LaunchedEffect(playbackControl.mediaId, playbackControl.positionMs, isDragging) {
        if (!isDragging) dragPositionMs = playbackControl.positionMs.toFloat()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelColor, contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = maxWidth.coerceAtMost(360.dp)),
                    shape = RoundedCornerShape(32.dp),
                    shadowElevation = 18.dp,
                    tonalElevation = 4.dp,
                    color = Color(0xFF201613),
                ) {
                    if (activeItem.coverUrl != null) {
                        AsyncImage(
                            model = activeItem.coverUrl,
                            contentDescription = activeItem.title,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF2F201A)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无封面", style = MaterialTheme.typography.titleMedium, color = SecondaryTextColor)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTitleDetails),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(activeItem.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开视频详情", tint = SecondaryTextColor, modifier = Modifier.size(28.dp))
                }
                AuthorRow(
                    authorName = activeItem.authorName,
                    authorAvatarUrl = activeItem.authorAvatarUrl,
                    canOpenCreatorCollections = activeItem.authorMid != null,
                    isFavorited = isFavorited,
                    onOpenCreatorCollections = onOpenCreatorCollections,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            val sliderMax = playbackControl.durationMs.takeIf { it > 0L }?.toFloat() ?: 1f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickSeekButton(
                    icon = Icons.Rounded.Replay,
                    contentDescription = "后退 15 秒",
                    enabled = playbackControl.hasActiveItem,
                    mirrored = false,
                    onClick = { onSeekBy(-15_000L) },
                )
                Slider(
                    value = dragPositionMs.coerceIn(0f, sliderMax),
                    onValueChange = {
                        isDragging = true
                        dragPositionMs = it
                    },
                    onValueChangeFinished = {
                        onSeekTo(dragPositionMs.roundToLong())
                        isDragging = false
                    },
                    valueRange = 0f..sliderMax,
                    enabled = playbackControl.hasActiveItem && playbackControl.durationMs > 0L,
                    modifier = Modifier.weight(1f),
                )
                QuickSeekButton(
                    icon = Icons.Rounded.Replay,
                    contentDescription = "前进 15 秒",
                    enabled = playbackControl.hasActiveItem,
                    mirrored = true,
                    onClick = { onSeekBy(15_000L) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(dragPositionMs.roundToLong()), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                Text(formatTime(playbackControl.durationMs.takeIf { it > 0L } ?: activeItem.durationMs), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CenteredControlSlot(Modifier.weight(1f)) { SideControlButton(Icons.Rounded.Tune, onOpenSettings) }
                CenteredControlSlot(Modifier.weight(1f)) {
                    TransportButton(playbackControl.hasActiveItem, onSkipPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一条", modifier = Modifier.size(24.dp), tint = PrimaryTextColor)
                    }
                }
                CenteredControlSlot(Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (playbackControl.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackControl.isPlaying) "暂停" else "播放",
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(
                                enabled = playbackControl.hasActiveItem,
                                onClick = onTogglePlayPause,
                            ),
                        tint = if (playbackControl.hasActiveItem) PrimaryTextColor else Color(0x66F7F2ED),
                    )
                }
                CenteredControlSlot(Modifier.weight(1f)) {
                    TransportButton(playbackControl.hasActiveItem, onSkipNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一条", modifier = Modifier.size(24.dp), tint = PrimaryTextColor)
                    }
                }
                CenteredControlSlot(Modifier.weight(1f)) {
                    SideControlButton(Icons.Rounded.QueueMusic, onOpenQueue, playback.queue.items.isNotEmpty())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDetailSheet(
    visible: Boolean,
    item: QueueItem?,
    onDismiss: () -> Unit,
) {
    if (!visible || item == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B120F),
        contentColor = PrimaryTextColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("视频详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            Text(
                text = "播放量 ${formatViewCount(item.viewCount)} · ${formatPublishDate(item.publishedAtMs)} · ${item.key.bvid}",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryTextColor,
            )
            Text(item.description?.takeIf { it.isNotBlank() } ?: "暂无简介", style = MaterialTheme.typography.bodyLarge, color = PrimaryTextColor)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    visible: Boolean,
    settings: PlayerSettings,
    onDismiss: () -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onPlayModeChanged: (PlayMode) -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B120F),
        contentColor = PrimaryTextColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("播放设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("倍速", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = PrimaryTextColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerSettingsStore.SUPPORTED_SPEEDS.forEach { speed ->
                        SelectPill(modifier = Modifier.weight(1f), selected = speed == settings.speed, onClick = { onPlaybackSpeedChanged(speed) }, text = formatSpeed(speed))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("播放模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = PrimaryTextColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectPill(Modifier.weight(1f), settings.playMode == PlayMode.AUTO_NEXT, { onPlayModeChanged(PlayMode.AUTO_NEXT) }, "自动连播")
                    SelectPill(Modifier.weight(1f), settings.playMode == PlayMode.SINGLE_LOOP, { onPlayModeChanged(PlayMode.SINGLE_LOOP) }, "单集循环")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    visible: Boolean,
    playback: PlannedPlayback?,
    activeIndex: Int,
    currentOrder: CollectionPlaybackOrder,
    onDismiss: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onCollectionPlaybackOrderChanged: (CollectionPlaybackOrder) -> Unit,
) {
    if (!visible || playback == null) return
    var showOrderOptions by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B120F),
        contentColor = PrimaryTextColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(playback.queue.collectionTitle ?: "当前合集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("共 ${playback.queue.items.size} 条", style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor)
                Surface(
                    modifier = Modifier.clickable { showOrderOptions = !showOrderOptions },
                    shape = RoundedCornerShape(16.dp),
                    color = SoftPanelColor,
                ) {
                    Text(
                        text = queueOrderLabel(currentOrder),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryTextColor,
                    )
                }
            }
            if (showOrderOptions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectPill(
                        modifier = Modifier.weight(1f),
                        selected = currentOrder == CollectionPlaybackOrder.COLLECTION_DEFAULT,
                        onClick = { onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.COLLECTION_DEFAULT) },
                        text = "合集顺序",
                    )
                    SelectPill(
                        modifier = Modifier.weight(1f),
                        selected = currentOrder == CollectionPlaybackOrder.PUBLISHED_ASC,
                        onClick = { onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.PUBLISHED_ASC) },
                        text = "时间正序",
                    )
                    SelectPill(
                        modifier = Modifier.weight(1f),
                        selected = currentOrder == CollectionPlaybackOrder.PUBLISHED_DESC,
                        onClick = { onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.PUBLISHED_DESC) },
                        text = "时间倒序",
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(playback.queue.items) { index, item ->
                    QueueSheetRow(item = item, index = index, isCurrent = index == activeIndex, onClick = { onPlayQueueIndex(index) })
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(
    authorName: String?,
    authorAvatarUrl: String?,
    canOpenCreatorCollections: Boolean,
    isFavorited: Boolean,
    onOpenCreatorCollections: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (authorAvatarUrl != null) {
            AsyncImage(
                model = authorAvatarUrl,
                contentDescription = authorName ?: "UP 主头像",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B2A23))
                    .clickable(enabled = canOpenCreatorCollections, onClick = onOpenCreatorCollections),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B2A23))
                    .clickable(enabled = canOpenCreatorCollections, onClick = onOpenCreatorCollections),
                contentAlignment = Alignment.Center,
            ) {
                Text(authorName?.take(1) ?: "主", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            }
        }
        Text(
            text = authorName ?: "未知 UP 主",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier.clickable(onClick = onToggleFavorite),
            shape = RoundedCornerShape(16.dp),
            color = if (isFavorited) FavoriteActiveBackground else FavoriteInactiveBackground,
        ) {
            Text(
                text = if (isFavorited) "已收藏" else "加入收藏",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF120B08),
            )
        }
    }
}

@Composable
private fun SideControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) SoftPanelColor else Color(0x222C1D17),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = PrimaryTextColor) }
    }
}

@Composable
private fun TransportButton(enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(54.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) SoftPanelColor else Color(0x222C1D17),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CenteredControlSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SelectPill(modifier: Modifier = Modifier, selected: Boolean, onClick: () -> Unit, text: String) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFFE0C7B2) else Color(0x332B1D17),
        tonalElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFF24140B) else PrimaryTextColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun QueueSheetRow(item: QueueItem, index: Int, isCurrent: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isCurrent) Color(0x4DE0C7B2) else Color(0x332B1D17),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(44.dp).background(Color(0x402E2019), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = PrimaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.authorName, formatTime(item.durationMs)).joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isCurrent) {
                Text("正在播放", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFFE5CE))
            }
        }
    }
}

@Composable
private fun QuickSeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    mirrored: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = if (mirrored) -1f else 1f),
            tint = if (enabled) PrimaryTextColor else Color(0x66F7F2ED),
        )
        Text(
            text = "15",
            modifier = Modifier.offset(y = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.88f),
            fontWeight = FontWeight.Bold,
            color = if (enabled) PrimaryTextColor else Color(0x66F7F2ED),
        )
    }
}

private data class CurrentDisplay(
    val activeIndex: Int,
    val item: QueueItem,
)

private fun resolveCurrentDisplay(playback: PlannedPlayback?, currentMediaId: String?): CurrentDisplay? {
    if (playback == null) return null
    val activeIndex = currentMediaId?.let { mediaId ->
        playback.queue.items.indexOfFirst { it.key.asStoreKey() == mediaId }.takeIf { it >= 0 }
    } ?: playback.plan.startIndex
    return CurrentDisplay(activeIndex = activeIndex, item = playback.queue.items[activeIndex])
}

private fun formatTime(valueMs: Long): String {
    if (valueMs <= 0L) return "00:00"
    val totalSeconds = valueMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatSpeed(speed: Float): String {
    val normalized = if (speed % 1f == 0f) speed.roundToLong().toString() else speed.toString().trimEnd('0').trimEnd('.')
    return "${normalized}x"
}

private fun queueOrderLabel(order: CollectionPlaybackOrder): String = when (order) {
    CollectionPlaybackOrder.COLLECTION_DEFAULT -> "合集顺序"
    CollectionPlaybackOrder.PUBLISHED_ASC -> "时间正序"
    CollectionPlaybackOrder.PUBLISHED_DESC -> "时间倒序"
}

private fun formatViewCount(viewCount: Long): String {
    if (viewCount <= 0L) return "0"
    if (viewCount < 10_000L) return viewCount.toString()
    val wan = viewCount / 10_000f
    val formatted = String.format(Locale.CHINA, "%.1f", wan).trimEnd('0').trimEnd('.')
    return "${formatted}万"
}

private fun formatPublishDate(publishTimeMs: Long): String {
    if (publishTimeMs <= 0L) return "未知日期"
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return formatter.format(Date(publishTimeMs))
}
