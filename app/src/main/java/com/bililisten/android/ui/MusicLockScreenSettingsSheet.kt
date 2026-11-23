package com.bililisten.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bililisten.android.lockscreen.MusicLockScreenActivity

@Composable
internal fun MusicLockScreenSettingsContent(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }
    val updateEnabled by rememberUpdatedState(onEnabledChanged)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permission = Settings.canDrawOverlays(context)
        if (pendingEnable && permission) updateEnabled(true)
        pendingEnable = false
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = Settings.canDrawOverlays(context)
                if (pendingEnable && permission) { updateEnabled(true); pendingEnable = false }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val requestPermission = {
        pendingEnable = true
        permissionError = false
        try {
            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) {
            pendingEnable = false
            permissionError = true
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaybackSettingsBackButton(onBack)
            Text("音乐锁屏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("亮屏听见此刻", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("播放期间锁屏后，再次亮屏显示时钟、黑胶唱片与节目控制。右滑回到系统锁屏，再使用原来的密码或指纹解锁。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("启用音乐锁屏", style = MaterialTheme.typography.titleSmall)
                Text(when { enabled && permission -> "已启用 · 下次播放时锁屏试试"; enabled -> "等待系统显示权限"; else -> "默认关闭" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(enabled, { value ->
                if (!value) { pendingEnable = false; updateEnabled(false) }
                else if (Settings.canDrawOverlays(context)) updateEnabled(true) else requestPermission()
            }, modifier = Modifier.semantics { contentDescription = "启用音乐锁屏" })
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (permission) "系统显示权限已允许" else "自动显示需要系统授权", style = MaterialTheme.typography.titleSmall)
                Text("允许“在其他应用上层显示”后，才能在锁屏亮屏时自动打开。仅在播放中使用，熄屏后退出，不会主动点亮或常亮屏幕。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!permission) TextButton(onClick = requestPermission) { Text("前往授权并启用") }
                if (permissionError) Text("未能打开授权页面，请在系统的应用设置中允许悬浮窗。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(onClick = { MusicLockScreenActivity.preview(context) }, modifier = Modifier.fillMaxWidth()) { Text("预览音乐锁屏") }
        Text("如果 ColorOS 未自动显示，请检查系统应用设置中的悬浮窗及后台显示限制。系统播放卡片仍可正常使用。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        }, modifier = Modifier.align(Alignment.End)) { Text("打开系统应用设置") }
    }
}
