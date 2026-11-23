package com.bililisten.android.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Read only during drawing: moving foliage must not recompose the player or regenerate its artwork. */
@Composable
internal fun rememberForestTime(focused: Boolean = LocalWindowInfo.current.isWindowFocused): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, focused) {
        if (!focused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrame = 0L
            var pendingNanos = 0L
            while (isActive) {
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    previousFrame = 0L
                    pendingNanos = 0L
                    delay(500)
                    continue
                }
                withInfiniteAnimationFrameNanos { frame ->
                    if (previousFrame != 0L) pendingNanos += (frame - previousFrame).coerceIn(0L, 100_000_000L)
                    previousFrame = frame
                    // About 30 fps is enough for slow wind and particles. All periods divide 120 s.
                    if (pendingNanos >= 32_000_000L) {
                        time.floatValue = (time.floatValue + pendingNanos / 1_000_000_000f) % 120f
                        pendingNanos = 0L
                    }
                }
            }
        }
    }
    return time
}
