package com.bililisten.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bililisten.android.data.BackgroundAudioSettings
import com.bililisten.android.data.BackgroundSound
import kotlin.math.roundToInt

@Composable
internal fun BackgroundSoundSettingsContent(
    settings: BackgroundAudioSettings,
    onChange: (BackgroundAudioSettings) -> Unit,
    onBack: () -> Unit,
) {
    var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
    var showCredits by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    AdaptiveSheetBody(header = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlaybackSettingsBackButton(onBack)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("背景音", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("离线可用 · 随节目播放", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Switch(settings.enabled, { onChange(settings.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = "启用背景音" })
        }
        Surface(Modifier.fillMaxWidth(), color = colors.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(settings.sound.label, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text(if (settings.enabled) "${(volume * 100).roundToInt()}%" else "已关闭", color = colors.onSurfaceVariant)
                }
                Text(settings.sound.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Slider(value = volume, onValueChange = { volume = it },
                    onValueChangeFinished = { onChange(settings.copy(volume = volume)) },
                    colors = SliderDefaults.colors(inactiveTrackColor = colors.outline.copy(alpha = .65f)),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "背景音音量" })
                Text("背景音音量独立调节；切换声音时平滑过渡。", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                BackgroundSound.entries.groupBy { it.group }.forEach { (group, sounds) ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(group, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        sounds.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { sound ->
                                    val selected = settings.sound == sound
                                    Surface(
                                        modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                                            onClick = { onChange(settings.copy(enabled = true, sound = sound, volume = volume)) }),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) colors.primary else colors.surfaceVariant,
                                        contentColor = if (selected) colors.onPrimary else colors.onSurface,
                                    ) {
                                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                            Text(sound.label, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Text("选择即启用；暂停节目时一同暂停。",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            TextButton(onClick = { showCredits = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                Text("音源与许可")
            }
        }
    }
    if (showCredits) {
        val context = LocalContext.current
        val credits = remember { context.assets.open("background/SOURCES.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showCredits = false }, title = { Text("音源与许可") },
            text = { SelectionContainer { Text(credits, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { showCredits = false }) { Text("关闭") } })
    }
}
