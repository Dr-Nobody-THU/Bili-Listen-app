package com.bililisten.android.widget

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bililisten.android.MainActivity
import com.bililisten.android.data.AppGraph
import com.bililisten.android.data.FavoriteCollection
import com.bililisten.android.player.AudioPlaybackService
import com.bililisten.android.ui.FavoriteCollectionRow
import com.bililisten.android.data.AppSkin
import com.bililisten.android.ui.ListenTheme

/** A floating activity keeps the launcher visible and needs no overlay permission. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FavoritePickerActivity : ComponentActivity() {
    private val repository by lazy { AppGraph.repository(this) }
    private var favorites by mutableStateOf(emptyList<FavoriteCollection>())
    private var activeId by mutableStateOf<String?>(null)
    private var skin by mutableStateOf(AppSkin.MINT_NIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        setContent {
            ListenTheme(skin) {
                FavoritePickerContent(favorites, activeId, onSelect = ::selectFavorite, onDismiss = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
                    })
            }
        }
        val widthDp = (resources.configuration.screenWidthDp - 32).coerceIn(240, 420)
        window.setLayout((widthDp * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        skin = AppGraph.playerSettings(this).load().skin
        favorites = repository.listFavorites()
        activeId = (repository.activePlayback ?: PlaybackWidgets.restoredPlayback(this))?.let(repository::favoriteIdOf)
    }

    internal fun selectFavorite(id: String) {
        if (repository.loadFavorite(id) == null) {
            favorites = repository.listFavorites()
            return
        }
        AudioPlaybackService.playFavorite(this, id)
        finish()
    }
}

@Composable
internal fun FavoritePickerContent(
    favorites: List<FavoriteCollection>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .75f).dp
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("切换合集", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "关闭合集选择") }
            }
            Text("选择后从上次位置继续播放", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (favorites.isEmpty()) {
                Text("还没有收藏合集，先到应用中添加。", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenApp, modifier = Modifier.align(Alignment.End)) { Text("打开应用") }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("favorite_picker_list"), verticalArrangement = Arrangement.spacedBy(10.dp), overscrollEffect = null) {
                    items(favorites, key = { it.id }) { favorite ->
                        FavoriteCollectionRow(favorite, favorite.id == activeId, onClick = { onSelect(favorite.id) })
                    }
                }
            }
        }
    }
}
