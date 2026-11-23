package com.bililisten.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForestMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clockAdvancesOnlyWhenResumedAndFocusedAndResumesWithoutAFastForward() {
        compose.mainClock.autoAdvance = false
        val focused = mutableStateOf(true)
        lateinit var owner: TestOwner
        lateinit var time: State<Float>
        compose.setContent {
            val rememberedOwner = remember { TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED } }
            SideEffect { owner = rememberedOwner }
            CompositionLocalProvider(LocalLifecycleOwner provides rememberedOwner) {
                time = rememberForestTime(focused.value)
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val moving = compose.runOnIdle { time.value }
        assertTrue("A visible forest should animate", moving > .8f && moving < 1.1f)
        compose.runOnIdle { focused.value = false }
        compose.mainClock.advanceTimeByFrame()
        val unfocused = compose.runOnIdle { time.value }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(unfocused, time.value, 0f); focused.value = true }
        // Recompose the focus change and dispatch repeatOnLifecycle to the Android main looper
        // before advancing the frame clock; these are distinct schedulers in this host test.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        val resumed = compose.runOnIdle { time.value }
        assertTrue("Focus resume should advance by <250 ms: before=$unfocused after=$resumed", resumed > unfocused && resumed - unfocused < .25f)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        val stopped = compose.runOnIdle { time.value }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertEquals(stopped, time.value, 0f) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue("Lifecycle resume should advance by <250 ms: before=$stopped after=${time.value}", time.value > stopped && time.value - stopped < .25f) }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
