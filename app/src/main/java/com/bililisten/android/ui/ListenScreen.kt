package com.bililisten.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.AppSkin
import com.bililisten.android.R
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.CollectionPlaybackOrder
import com.bililisten.android.data.ContentKey
import com.bililisten.android.data.AudioCacheEntry
import com.bililisten.android.data.AudioCacheSnapshot
import com.bililisten.android.data.CachePhase
import com.bililisten.android.data.CreatorCollectionSummary
import com.bililisten.android.data.FavoriteCollection
import com.bililisten.android.data.formatListeningTime
import com.bililisten.android.data.PlayMode
import com.bililisten.android.data.PlannedPlayback
import com.bililisten.android.data.PlayerSettings
import com.bililisten.android.data.PlayerSettingsStore
import com.bililisten.android.data.QueueItem
import com.bililisten.android.data.ResumePolicy
import com.bililisten.android.data.SpecialFavorites
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

private val PrimaryTextColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val SecondaryTextColor: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AccentColor: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PanelColor: Color @Composable get() = MaterialTheme.colorScheme.surface
private val SoftPanelColor: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val FavoriteInactiveBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val FavoriteActiveBackground: Color @Composable get() = AccentColor
private val TopControlHeight = 56.dp
private val CompactTopControlHeight = 48.dp
private val TopSideButtonWidth = 82.dp

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
    onAllowMobileStreamingChanged: (Boolean) -> Unit,
    onRetryCache: () -> Unit,
    onFadePlaybackChanged: (Boolean) -> Unit,
    onBackgroundAudioChanged: (BackgroundAudioSettings) -> Unit,
    onMusicLockScreenChanged: (Boolean) -> Unit = {},
    onSkinChanged: (AppSkin) -> Unit = {},
    onToggleSpecialFavorite: () -> Unit = {},
    onSaveSpecialFavoriteOrder: (List<ContentKey>) -> Unit = {},
    onRecordCollectionSwitchChanged: (Boolean) -> Unit = {},
    onConfirmRecordCollection: (String) -> Unit = onOpenFavorite,
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showTitleSheet by rememberSaveable { mutableStateOf(false) }
    var showFavoritesDrawer by rememberSaveable { mutableStateOf(false) }
    var showCreatorDrawer by rememberSaveable { mutableStateOf(false) }
    var inputExpanded by rememberSaveable { mutableStateOf(false) }
    var recordBrowse by remember { mutableStateOf<RecordBrowseSession?>(null) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val browseProgress = animateFloatAsState(if (recordBrowse?.active == true) 1f else 0f,
        tween(320, easing = FastOutSlowInEasing), label = "record browser entrance",
        finishedListener = { if (it == 0f && recordBrowse?.active == false) recordBrowse = null })
    BackHandler(enabled = recordBrowse?.active == true) { recordBrowse?.active = false }
    LaunchedEffect(state.playerSettings.recordCollectionSwitch, state.plannedPlayback?.let(::recordCollectionId)) {
        if (!state.playerSettings.recordCollectionSwitch || recordBrowse?.originId != state.plannedPlayback?.let(::recordCollectionId)) {
            recordBrowse?.active = false
        }
    }
    val display = resolveCurrentDisplay(state.plannedPlayback, state.playbackControl.mediaId)
    val lifecycleOwner = LocalLifecycleOwner.current
    val landscape = isLandscapeWindow()

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
                recordBrowse = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ListenTheme(state.playerSettings.skin) {
        TitleDetailSheet(
            visible = showTitleSheet && display != null,
            item = display?.item,
            onDismiss = { showTitleSheet = false },
        )
        if (showSettingsSheet) PlaybackSettingsSheet(
            settings = state.playerSettings,
            onDismiss = { showSettingsSheet = false },
            onPlaybackSpeedChanged = onPlaybackSpeedChanged,
            onPlayModeChanged = onPlayModeChanged,
            onAllowMobileStreamingChanged = onAllowMobileStreamingChanged,
            onFadePlaybackChanged = onFadePlaybackChanged,
            onBackgroundAudioChanged = onBackgroundAudioChanged,
            onMusicLockScreenChanged = onMusicLockScreenChanged,
            onSkinChanged = onSkinChanged,
            onRecordCollectionSwitchChanged = onRecordCollectionSwitchChanged,
        )
        QueueSheet(
            visible = showQueueSheet && state.plannedPlayback != null && display != null,
            playback = state.plannedPlayback,
            activeIndex = display?.activeIndex ?: 0,
            currentOrder = state.currentCollectionPlaybackOrder,
            cache = state.cacheSnapshot,
            isSortingOrder = state.isSortingOrder,
            onRetryCache = onRetryCache,
            onDismiss = { showQueueSheet = false },
            onPlayQueueIndex = { index ->
                onPlayQueueIndex(index)
            },
            onCollectionPlaybackOrderChanged = onCollectionPlaybackOrderChanged,
            manualItems = state.specialFavoriteManualItems,
            onSaveManualOrder = onSaveSpecialFavoriteOrder,
        )
        val playerHeader: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("player_header")
                    .then(if (recordBrowse?.active == true) Modifier.clearAndSetSemantics { } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (landscape) {
                    Row(Modifier.testTag("player_header_brand"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_stat_listen), null, Modifier.size(22.dp), tint = AccentColor)
                        Text("  栖声", style = MaterialTheme.typography.titleMedium, color = PrimaryTextColor, fontWeight = FontWeight.SemiBold)
                    }
                }
                TopInputBar(
                    modifier = Modifier.weight(1f),
                    input = state.input,
                    expanded = inputExpanded,
                    error = state.error,
                    onExpandedChange = { inputExpanded = it },
                    onInputChanged = onInputChanged,
                    compact = landscape,
                )
                if (inputExpanded) {
                    InputConfirmButton(
                        isLoading = state.isLoading,
                        compact = landscape,
                        onClick = {
                            inputExpanded = false
                            onPlayRequested(ResumePolicy.PREFER_REQUESTED_ITEM)
                        },
                    )
                } else {
                    FavoriteDrawerButton(
                        hasFavorites = state.favorites.isNotEmpty(),
                        compact = landscape,
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
        }
        val dismissLandscapeInput: (() -> Unit)? = if (landscape && inputExpanded) ({ inputExpanded = false }) else null
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootPosition = it.positionInRoot() }
                .dismissRecordBrowseOutside(recordBrowse, rootPosition)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surfaceBright, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceDim),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(start = 18.dp, end = 18.dp, top = if (landscape) 2.dp else 14.dp,
                        bottom = if (landscape) 8.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 14.dp),
            ) {
                if (!landscape) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_stat_listen), contentDescription = null, tint = AccentColor, modifier = Modifier.size(22.dp))
                        Text("  栖声", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = PrimaryTextColor)
                        Spacer(Modifier.weight(1f))
                        Text("随时听，接着听", style = MaterialTheme.typography.labelMedium, color = SecondaryTextColor)
                    }
                }
                if (!landscape) playerHeader()
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (state.plannedPlayback == null || display == null) {
                        EmptyPlayerCard(landscape, playerHeader, dismissLandscapeInput)
                    } else {
                        PlayerScene(
                            playback = state.plannedPlayback,
                            display = display,
                            playbackControl = state.playbackControl,
                            playbackError = state.playbackError,
                            listeningTimeMs = state.totalListeningTimeMs,
                            isFavorited = state.currentFavoriteId != null,
                            onToggleFavorite = onToggleFavorite,
                            isSpecialFavorited = state.isCurrentSpecialFavorite,
                            onToggleSpecialFavorite = onToggleSpecialFavorite,
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
                            landscape = landscape,
                            landscapeHeader = playerHeader,
                            onDismissInput = dismissLandscapeInput,
                            recordBrowse = recordBrowse,
                            browseProgress = browseProgress,
                            onBrowseCollections = if (state.playerSettings.recordCollectionSwitch) ({
                                inputExpanded = false
                                recordBrowse = RecordBrowseSession(state.plannedPlayback.copy(plan = state.plannedPlayback.plan.copy(
                                    item = display.item, startIndex = display.activeIndex)), state.favorites)
                            }) else null,
                            onConfirmRecordCollection = onConfirmRecordCollection,
                        )
                    }
                    if (inputExpanded && !landscape) {
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
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.height(if (compact) CompactTopControlHeight else TopControlHeight).testTag("player_import_input"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(22.dp),
        border = if (compact && expanded) BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline) else null,
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
                    text = if (input.isBlank()) (if (compact) "导入 BV / AU" else "导入 BV / AU 或链接") else input,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开输入栏", tint = SecondaryTextColor, modifier = Modifier.size(24.dp))
            }
        } else if (compact) {
            // An outlined field's floating label needs a taller frame. Keep landscape's
            // editor in the same 48 dp frame as its collapsed state and its neighboring button.
            BasicTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "BV / AU 号或链接" },
                textStyle = MaterialTheme.typography.titleSmall.copy(color = PrimaryTextColor),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { editor ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                        if (error != null) Text(error, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (input.isBlank()) Text("BV / AU 号或链接", style = MaterialTheme.typography.titleSmall,
                                color = SecondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            editor()
                        }
                    }
                },
            )
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxSize(),
                label = {
                    Text(
                        text = error ?: "BV / AU 号或链接",
                        color = if (error != null) Color(0xFFFFD1CF) else SecondaryTextColor,
                    )
                },
                placeholder = { Text("B 站视频或纯音频链接") },
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
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.width(TopSideButtonWidth).height(if (compact) CompactTopControlHeight else TopControlHeight)
            .testTag("player_header_action"),
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
    compact: Boolean = false,
) {
    Surface(
        modifier = Modifier.width(TopSideButtonWidth).height(if (compact) CompactTopControlHeight else TopControlHeight)
            .testTag("player_header_action").clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (hasFavorites) AccentColor else SoftPanelColor,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("收藏夹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (hasFavorites) MaterialTheme.colorScheme.onPrimary else PrimaryTextColor)
            if (!compact) Text(if (hasFavorites) "查看续播" else "暂无收藏", style = MaterialTheme.typography.bodySmall, color = if (hasFavorites) MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f) else SecondaryTextColor, maxLines = 1)
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
            color = PanelColor,
            tonalElevation = 10.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
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
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), overscrollEffect = null) {
                        items(favorites, key = { it.id }) { favorite ->
                            FavoriteCollectionRow(
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
internal fun FavoriteCollectionRow(
    favorite: FavoriteCollection,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else SoftPanelColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceDim) {
                if (favorite.id == SpecialFavorites.ID) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, null, Modifier.size(28.dp), tint = AccentColor)
                    }
                } else if (favorite.coverUrl != null) {
                    CachedArtwork(
                        model = favorite.coverUrl,
                        contentDescription = favorite.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SoftPanelColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = SecondaryTextColor)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(favorite.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = PrimaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("第 ${favorite.playback.plan.startIndex + 1} / ${favorite.itemCount} 条${if (isActive) " · 当前合集" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor, maxLines = 1)
                if (favorite.id == SpecialFavorites.ID) Text(
                    "${favorite.playback.queue.items.distinctBy { it.key.bvid }.size} 个稿件 · 按音频收藏",
                    style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor, maxLines = 1)
                Text("累计收听 ${formatListeningTime(favorite.listeningTimeMs)}", style = MaterialTheme.typography.bodySmall,
                    color = AccentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            color = PanelColor,
            tonalElevation = 10.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
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
                            overscrollEffect = null,
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
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else SoftPanelColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.width(108.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceDim,
            ) {
                if (summary.coverUrl != null) {
                    CachedArtwork(
                        model = summary.coverUrl,
                        contentDescription = summary.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(SoftPanelColor),
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
private fun EmptyPlayerCard(landscape: Boolean, landscapeHeader: @Composable () -> Unit, onDismissInput: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = PanelColor, contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(30.dp),
    ) {
        PlayerBody(landscape = landscape, landscapeHeader = landscapeHeader, onDismissInput = onDismissInput,
            artwork = { VinylRecord(null, false) }) {
            Text("导入 B 站视频、合集或纯音频链接，开始聆听。", style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerBody(
    landscape: Boolean,
    landscapeHeader: @Composable () -> Unit,
    onDismissInput: (() -> Unit)?,
    artwork: @Composable () -> Unit,
    browsing: Boolean = false,
    controls: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp,
        top = if (landscape) 4.dp else 16.dp, bottom = if (landscape) 10.dp else 16.dp)) {
        if (landscape) {
            Row(Modifier.fillMaxSize().testTag("landscape_player"), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(.44f).fillMaxHeight().testTag("player_artwork_area"), contentAlignment = Alignment.Center) {
                    artwork()
                    if (onDismissInput != null) Box(Modifier.fillMaxSize().clickable(onClick = onDismissInput))
                }
                Column(Modifier.weight(.56f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    landscapeHeader()
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("player_controls_viewport")) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), reverseScrolling = true)
                            .then(if (browsing) Modifier.clearAndSetSemantics { } else Modifier)
                            .testTag("player_controls"), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), content = controls)
                        // Dismiss taps cover the player, while the header's editor and confirm button remain interactive.
                        if (onDismissInput != null) Box(Modifier.fillMaxSize().clickable(onClick = onDismissInput))
                    }
                }
            }
        } else {
            val gap = 14.dp.coerceAtMost(maxHeight)
            // Preserve a little artwork space when the keyboard or larger fonts reduce the height.
            // The controls can scroll from their bottom edge in this constrained case.
            val minimumArtworkHeight = 96.dp.coerceAtMost(maxHeight / 4)
            val controlsMaxHeight = (maxHeight - minimumArtworkHeight - gap).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.fillMaxWidth().weight(1f).testTag("player_artwork_area"), contentAlignment = Alignment.Center) {
                    artwork()
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .then(if (browsing) Modifier.clearAndSetSemantics { } else Modifier)
                        .testTag("player_controls"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = controls,
                )
            }
        }
    }
}

@Composable
private fun PlayerScene(
    playback: PlannedPlayback,
    display: CurrentDisplay,
    playbackControl: PlaybackControlState,
    playbackError: String?,
    listeningTimeMs: Long,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    isSpecialFavorited: Boolean,
    onToggleSpecialFavorite: () -> Unit,
    onOpenCreatorCollections: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenTitleDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    landscape: Boolean,
    landscapeHeader: @Composable () -> Unit,
    onDismissInput: (() -> Unit)?,
    recordBrowse: RecordBrowseSession?,
    browseProgress: State<Float>,
    onBrowseCollections: (() -> Unit)?,
    onConfirmRecordCollection: (String) -> Unit,
) {
    val activeItem = display.item
    var isDragging by remember(playbackControl.mediaId) { mutableStateOf(false) }
    var dragPositionMs by remember(playbackControl.mediaId) { mutableFloatStateOf(playbackControl.positionMs.toFloat()) }
    val forestState = rememberSaveable { mutableStateOf(false) }
    val sourceLabel = if (playbackError != null) "播放已停止" else when (playbackControl.localAudio) {
        true -> "本地缓存播放"
        false -> "在线播放"
        null -> "准备就绪"
    }

    LaunchedEffect(playbackControl.mediaId, playbackControl.positionMs, isDragging) {
        if (!isDragging) dragPositionMs = playbackControl.positionMs.toFloat()
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = PanelColor, contentColor = PrimaryTextColor),
        shape = RoundedCornerShape(32.dp),
    ) {
        PlayerBody(landscape = landscape, landscapeHeader = landscapeHeader, onDismissInput = onDismissInput,
            browsing = recordBrowse?.active == true, artwork = {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ListeningArtwork(
                        coverUrl = activeItem.coverUrl,
                        playing = playbackControl.isPlaying,
                        listeningTimeMs = listeningTimeMs,
                        forestState = forestState,
                        recordBrowse = recordBrowse,
                        browseProgress = browseProgress,
                        onBrowseCollections = onBrowseCollections,
                        onConfirmRecordCollection = onConfirmRecordCollection,
                        artworkKey = recordCollectionId(playback),
                        landscape = landscape,
                    )
                }
                if (landscape) Text(sourceLabel, style = MaterialTheme.typography.labelSmall, color = SecondaryTextColor)
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 10.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = if (landscape) 52.dp else 74.dp)) {
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - browseProgress.value },
                        verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 10.dp)) {
                        Text(
                            "${display.activeIndex + 1} / ${playback.queue.items.size}  ·  ${playback.queue.collectionTitle ?: "单条音频"}",
                            modifier = Modifier.testTag("player_episode_count"),
                            style = MaterialTheme.typography.labelMedium, color = AccentColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTitleDetails),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(activeItem.title, modifier = Modifier.weight(1f), style = if (landscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryTextColor, maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = if (activeItem.key.audioId != null) "展开音频详情" else "展开视频详情", tint = SecondaryTextColor, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (recordBrowse != null) Box(Modifier.fillMaxWidth().graphicsLayer { alpha = browseProgress.value }) {
                        RecordCollectionTitle(recordBrowse, landscape)
                    }
                }
                AuthorRow(
                    authorName = activeItem.authorName,
                    authorAvatarUrl = activeItem.authorAvatarUrl,
                    canOpenCreatorCollections = activeItem.authorMid != null && activeItem.key.audioId == null,
                    isFavorited = isFavorited,
                    onOpenCreatorCollections = onOpenCreatorCollections,
                    onToggleFavorite = onToggleFavorite,
                    isSpecialFavorited = isSpecialFavorited,
                    onToggleSpecialFavorite = onToggleSpecialFavorite,
                    isSpecialPlaylist = playback.queue.collectionId == SpecialFavorites.ID,
                )
            }
            val sliderMax = playbackControl.durationMs.takeIf { it > 0L }?.toFloat() ?: 1f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (landscape) Text(formatTime(dragPositionMs.roundToLong()), style = MaterialTheme.typography.labelSmall, color = SecondaryTextColor)
                QuickSeekButton(
                    icon = Icons.Rounded.Replay,
                    contentDescription = "后退 15 秒",
                    enabled = playbackControl.hasActiveItem,
                    mirrored = false,
                    onClick = { onSeekBy(-15_000L) },
                )
                Slider(
                    colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant),
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
                if (landscape) Text(formatTime(playbackControl.durationMs.takeIf { it > 0L } ?: activeItem.durationMs), style = MaterialTheme.typography.labelSmall, color = SecondaryTextColor)
            }
            if (!landscape) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(dragPositionMs.roundToLong()), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                    Text(formatTime(playbackControl.durationMs.takeIf { it > 0L } ?: activeItem.durationMs), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                SideControlButton(Icons.Rounded.Tune, onOpenSettings, modifier = Modifier.weight(1f, fill = false))
                TransportButton(playbackControl.hasActiveItem, onSkipPrevious, Modifier.weight(1f, fill = false)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一条", modifier = Modifier.size(24.dp), tint = PrimaryTextColor)
                }
                Surface(
                    onClick = onTogglePlayPause,
                    enabled = playbackControl.hasActiveItem,
                    modifier = Modifier.weight(1f, fill = false).size(60.dp),
                    shape = CircleShape,
                    color = AccentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playbackControl.isBuffering) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else Icon(
                            imageVector = if (playbackControl.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackControl.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                TransportButton(playbackControl.hasActiveItem, onSkipNext, Modifier.weight(1f, fill = false)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一条", modifier = Modifier.size(24.dp), tint = PrimaryTextColor)
                }
                SideControlButton(Icons.AutoMirrored.Rounded.QueueMusic, onOpenQueue, playback.queue.items.isNotEmpty(), Modifier.weight(1f, fill = false))
            }
            if (playbackError != null) {
                Text(playbackError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (!landscape) Text(sourceLabel, Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall, color = SecondaryTextColor)
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

    StableBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        contentColor = PrimaryTextColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (item.key.audioId != null) "音频详情" else "视频详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
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

@Composable
private fun SkinPicker(selected: AppSkin, onSkinChanged: (AppSkin) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("界面皮肤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSkin.entries.forEach { skin ->
                val colors = listenColorScheme(skin)
                val active = skin == selected
                Surface(
                    modifier = Modifier.weight(1f).selectable(active, role = Role.RadioButton, onClick = { onSkinChanged(skin) }),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.background,
                    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) colors.primary else colors.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).background(colors.primary, CircleShape))
                        Text(skin.label, color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackSettingsContent(
    settings: PlayerSettings,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onPlayModeChanged: (PlayMode) -> Unit,
    onAllowMobileStreamingChanged: (Boolean) -> Unit,
    onFadePlaybackChanged: (Boolean) -> Unit,
    onBackgroundAudioChanged: (BackgroundAudioSettings) -> Unit,
    onOpenBackground: () -> Unit,
    onMusicLockScreenChanged: (Boolean) -> Unit,
    onOpenMusicLockScreen: () -> Unit,
    onSkinChanged: (AppSkin) -> Unit,
    onRecordCollectionSwitchChanged: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("播放设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
        SkinPicker(settings.skin, onSkinChanged)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("长按唱片切换合集", style = MaterialTheme.typography.titleSmall)
                Text("竖屏左右、横屏上下浏览收藏合集，点唱片确认播放；点其他区域取消。森林模式不触发。",
                    style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
            }
            Switch(settings.recordCollectionSwitch, onRecordCollectionSwitchChanged,
                modifier = Modifier.semantics { contentDescription = "长按唱片切换合集" })
        }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("声音淡入淡出", style = MaterialTheme.typography.titleSmall)
                Text("播放和暂停时，音量在约 0.4 秒内平滑过渡。", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
            }
            Switch(checked = settings.fadePlayback, onCheckedChange = onFadePlaybackChanged,
                modifier = Modifier.semantics { contentDescription = "声音淡入淡出" })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("背景音", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenBackground, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics { contentDescription = "选择背景音" }) {
                    Text("${settings.backgroundAudio.sound.label} · ${(settings.backgroundAudio.volume * 100).roundToLong()}%   选择与音量 ›")
                }
            }
            Switch(settings.backgroundAudio.enabled, { onBackgroundAudioChanged(settings.backgroundAudio.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = "启用背景音" })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("音乐锁屏", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onOpenMusicLockScreen, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics { contentDescription = "音乐锁屏设置" }) {
                    Text("黑胶与时钟   预览与权限 ›")
                }
            }
            Switch(settings.musicLockScreen, { if (it) onOpenMusicLockScreen() else onMusicLockScreenChanged(false) },
                modifier = Modifier.semantics { contentDescription = "音乐锁屏开关" })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("允许移动网络在线播放", style = MaterialTheme.typography.titleSmall)
                Text("默认关闭。未缓存的音频会停止播放；已缓存音频始终优先从本机读取。", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
            }
            Switch(checked = settings.allowMobileStreaming, onCheckedChange = onAllowMobileStreamingChanged)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    visible: Boolean,
    playback: PlannedPlayback?,
    activeIndex: Int,
    currentOrder: CollectionPlaybackOrder,
    cache: AudioCacheSnapshot,
    isSortingOrder: Boolean,
    onRetryCache: () -> Unit,
    onDismiss: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onCollectionPlaybackOrderChanged: (CollectionPlaybackOrder) -> Unit,
    manualItems: List<QueueItem>,
    onSaveManualOrder: (List<ContentKey>) -> Unit,
) {
    if (!visible || playback == null) return
    var showOrderOptions by rememberSaveable { mutableStateOf(false) }
    var editingOrder by rememberSaveable { mutableStateOf(false) }
    val specialPlaylist = playback.queue.collectionId == SpecialFavorites.ID
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = activeIndex)
    val scope = rememberCoroutineScope()
    val showLocate by remember(listState, activeIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount > 0 && layout.visibleItemsInfo.none {
                it.index == activeIndex && it.offset + it.size > layout.viewportStartOffset && it.offset < layout.viewportEndOffset
            }
        }
    }
    LaunchedEffect(currentOrder) { listState.scrollToItem(activeIndex) }

    StableBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = if (isLandscapeWindow()) 1000.dp else BottomSheetDefaults.SheetMaxWidth,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = PanelColor,
        contentColor = PrimaryTextColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        BackHandler(enabled = editingOrder) { editingOrder = false }
        if (editingOrder) {
            SpecialFavoritesOrderEditor(
                initialItems = manualItems,
                currentKey = playback.queue.items.getOrNull(activeIndex)?.key,
                onCancel = { editingOrder = false },
                onSave = { keys ->
                    onSaveManualOrder(keys)
                    editingOrder = false
                    scope.launch { listState.scrollToItem(0) }
                },
            )
        } else {
            AdaptiveSheetBody(header = {
                Text(playback.queue.collectionTitle ?: "当前合集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("共 ${playback.queue.items.size} 条", style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor)
                    Surface(
                        modifier = Modifier.testTag("queue_order_toggle")
                            .semantics {
                                contentDescription = if (showOrderOptions) "收起顺序选项" else "展开顺序选项"
                                stateDescription = queueOrderLabel(currentOrder.forCollection(playback.queue.collectionId))
                            }
                            .clickable(role = Role.Button) { showOrderOptions = !showOrderOptions },
                        shape = RoundedCornerShape(16.dp),
                        color = SoftPanelColor,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(queueOrderLabel(currentOrder.forCollection(playback.queue.collectionId)),
                                style = MaterialTheme.typography.labelLarge, color = PrimaryTextColor)
                            Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)
                                .graphicsLayer { rotationZ = if (showOrderOptions) 180f else 0f }, tint = SecondaryTextColor)
                        }
                    }
                }
                if (showOrderOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("queue_order_options"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (specialPlaylist) {
                            listOf(CollectionPlaybackOrder.FAVORITED_ASC, CollectionPlaybackOrder.FAVORITED_DESC, CollectionPlaybackOrder.MANUAL).forEach { order ->
                                SelectPill(
                                    modifier = Modifier.weight(1f).testTag("special_order_${order.name.lowercase()}"),
                                    selected = currentOrder.forCollection(SpecialFavorites.ID) == order,
                                    onClick = {
                                        showOrderOptions = false
                                        if (order == CollectionPlaybackOrder.MANUAL) editingOrder = true
                                        else onCollectionPlaybackOrderChanged(order)
                                    },
                                    text = queueOrderLabel(order),
                                    compact = true,
                                )
                            }
                        } else {
                            SelectPill(
                                modifier = Modifier.weight(1f),
                                selected = currentOrder == CollectionPlaybackOrder.COLLECTION_DEFAULT,
                                onClick = { showOrderOptions = false; onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.COLLECTION_DEFAULT) },
                                text = "合集顺序",
                            )
                            SelectPill(
                                modifier = Modifier.weight(1f),
                                selected = currentOrder == CollectionPlaybackOrder.PUBLISHED_ASC,
                                onClick = { showOrderOptions = false; onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.PUBLISHED_ASC) },
                                text = "时间正序",
                            )
                            SelectPill(
                                modifier = Modifier.weight(1f),
                                selected = currentOrder == CollectionPlaybackOrder.PUBLISHED_DESC,
                                onClick = { showOrderOptions = false; onCollectionPlaybackOrderChanged(CollectionPlaybackOrder.PUBLISHED_DESC) },
                                text = "时间倒序",
                            )
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = SoftPanelColor) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("已缓存 ${cache.cachedCount} 条 · ${formatBytes(cache.cachedBytes)}", style = MaterialTheme.typography.labelLarge, color = AccentColor)
                                Text(if (cache.autoCacheEnabled) "当前及后续 ${maxOf(cache.targetCount - 1, 0)} 条：${cache.readyTargetCount}/${cache.targetCount} 已就绪"
                                    else "收藏合集后开启自动缓存", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                            }
                            TextButton(onClick = onRetryCache, enabled = cache.wifiAvailable && cache.autoCacheEnabled) { Text("重试缓存") }
                        }
                    }
                }
                if (isSortingOrder) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在补齐发布时间…", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                } else if (currentOrder.usesPublicationTime && !specialPlaylist && playback.queue.items.any { it.publishedAtMs == 0L }) {
                    Text("部分发布时间缺失，列在末尾；连接 Wi-Fi 后可重新选择排序。", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                } else if (playback.queue.collectionId?.startsWith("video:") == true) {
                    Text("分 P 共用发布时间，时间倒序按分 P 倒序播放。", style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor)
                }
            }) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("playback_queue"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(playback.queue.items, key = { _, item -> item.key.asStoreKey() }) { index, item ->
                            Box(Modifier.testTag("queue_item_$index")) {
                                QueueSheetRow(item = item, index = index, isCurrent = index == activeIndex, cacheEntry = cache.entries[item.key.asStoreKey()], onClick = { onPlayQueueIndex(index) })
                            }
                        }
                    }
                    if (showLocate) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(48.dp)
                            .semantics { contentDescription = "定位当前播放" }
                            .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(activeIndex, 0) } },
                            contentAlignment = Alignment.Center) {
                            Surface(Modifier.size(36.dp), shape = CircleShape, color = AccentColor, shadowElevation = 4.dp) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
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
    isSpecialFavorited: Boolean,
    onToggleSpecialFavorite: () -> Unit,
    isSpecialPlaylist: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (authorAvatarUrl != null) {
            CachedArtwork(
                model = authorAvatarUrl,
                contentDescription = authorName ?: "UP 主头像",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = canOpenCreatorCollections, onClick = onOpenCreatorCollections),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
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
        IconToggleButton(
            checked = isSpecialFavorited,
            onCheckedChange = { onToggleSpecialFavorite() },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "特别收藏当前音频" },
        ) {
            Icon(if (isSpecialFavorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null, modifier = Modifier.size(25.dp),
                tint = if (isSpecialFavorited) AccentColor else SecondaryTextColor)
        }
        Surface(
            modifier = Modifier.clickable(enabled = !isSpecialPlaylist, onClick = onToggleFavorite),
            shape = RoundedCornerShape(16.dp),
            color = if (isFavorited) FavoriteActiveBackground else FavoriteInactiveBackground,
        ) {
            Text(
                text = if (isSpecialPlaylist) SpecialFavorites.TITLE else if (isFavorited) "已收藏" else "加入收藏",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isFavorited) MaterialTheme.colorScheme.onPrimary else PrimaryTextColor,
            )
        }
    }
}

@Composable
private fun SideControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) SoftPanelColor else SoftPanelColor.copy(alpha = .3f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = if (icon == Icons.Rounded.Tune) "播放设置" else "播放队列", tint = PrimaryTextColor)
        }
    }
}

@Composable
private fun TransportButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.size(54.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) SoftPanelColor else SoftPanelColor.copy(alpha = .3f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun SelectPill(modifier: Modifier = Modifier, selected: Boolean, onClick: () -> Unit, text: String, compact: Boolean = false) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) AccentColor else SoftPanelColor,
        tonalElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 12.dp),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else PrimaryTextColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun QueueSheetRow(item: QueueItem, index: Int, isCurrent: Boolean, cacheEntry: AudioCacheEntry?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else SoftPanelColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = PrimaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.authorName, formatTime(item.durationMs)).joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = SecondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.publishedAtMs > 0) Text(formatPublishDate(item.publishedAtMs), style = MaterialTheme.typography.labelSmall, color = SecondaryTextColor)
                Text(cacheEntryLabel(cacheEntry), style = MaterialTheme.typography.labelMedium,
                    color = when (cacheEntry?.phase) {
                        CachePhase.CACHED -> AccentColor
                        CachePhase.FAILED -> MaterialTheme.colorScheme.error
                        else -> SecondaryTextColor
                    })
                if (cacheEntry?.phase == CachePhase.DOWNLOADING && cacheEntry.totalBytes > 0) {
                    LinearProgressIndicator(progress = { (cacheEntry.bytes.toFloat() / cacheEntry.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (isCurrent) {
                Text("当前", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentColor)
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
            tint = if (enabled) PrimaryTextColor else PrimaryTextColor.copy(alpha = .4f),
        )
        Text(
            text = "15",
            modifier = Modifier.offset(y = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.88f),
            fontWeight = FontWeight.Bold,
            color = if (enabled) PrimaryTextColor else PrimaryTextColor.copy(alpha = .4f),
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
    CollectionPlaybackOrder.FAVORITED_ASC -> "收藏时间正序"
    CollectionPlaybackOrder.FAVORITED_DESC -> "收藏时间倒序"
    CollectionPlaybackOrder.MANUAL -> "手动排序"
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> "${bytes / 1024} KB"
}

private fun cacheEntryLabel(entry: AudioCacheEntry?): String = when (entry?.phase) {
    CachePhase.CACHED -> "已缓存 · ${formatBytes(entry.bytes)}"
    CachePhase.DOWNLOADING -> if (entry.totalBytes > 0) "正在缓存 · ${entry.bytes * 100 / entry.totalBytes}%" else "正在缓存 · ${formatBytes(entry.bytes)}"
    CachePhase.QUEUED -> "等待缓存"
    CachePhase.WAITING_WIFI -> "等待 Wi-Fi 缓存"
    CachePhase.FAILED -> "缓存失败 · ${entry.error ?: "请重试"}"
    else -> "未缓存"
}

@Composable
internal fun CachedArtwork(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    val wifi = AppGraph.repository(context).isWifiConnected()
    val request = remember(model, wifi) {
        ImageRequest.Builder(context).data(model)
            .networkCachePolicy(if (wifi) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(model = request, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}
