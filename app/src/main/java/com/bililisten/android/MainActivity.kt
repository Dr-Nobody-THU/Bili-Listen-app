package com.bililisten.android

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.bililisten.android.ui.ListenScreen
import com.bililisten.android.ui.ListenViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ListenViewModel> {
        ListenViewModel.factory(application)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op for first pass */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            ListenScreen(
                state = viewModel.uiState,
                onInputChanged = viewModel::setInput,
                onPlayRequested = viewModel::startPlayback,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSeekBy = viewModel::seekBy,
                onSkipPrevious = viewModel::skipPrevious,
                onSkipNext = viewModel::skipNext,
                onPlayQueueIndex = viewModel::playQueueIndex,
                onPlaybackSpeedChanged = viewModel::updatePlaybackSpeed,
                onPlayModeChanged = viewModel::updatePlayMode,
                onToggleFavorite = viewModel::toggleCurrentFavorite,
                onOpenFavorite = viewModel::openFavorite,
                onOpenCreatorCollections = viewModel::loadCreatorCollectionsForCurrentAuthor,
                onOpenCreatorCollection = viewModel::openCreatorCollection,
                onCollectionPlaybackOrderChanged = viewModel::updateCollectionPlaybackOrder,
            )
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val input = extractInput(intent) ?: return
        val autoStart = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW
        viewModel.importInput(input, autoStart = autoStart)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun extractInput(intent: Intent?): String? {
        if (intent == null) {
            return null
        }
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
            Intent.ACTION_VIEW -> intent.dataString?.trim()?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }
}
