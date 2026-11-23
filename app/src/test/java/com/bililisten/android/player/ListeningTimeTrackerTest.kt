package com.bililisten.android.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningTimeTrackerTest {
    @Test fun pausesBufferingAndSwitchingCollectionsDoNotCountAsListening() {
        var now = 0L
        val totals = mutableMapOf<String, Long>()
        val tracker = ListeningTimeTracker({ now }) { id, duration -> totals[id] = (totals[id] ?: 0) + duration }
        tracker.update("A", false) // Loading.
        now = 9_000
        tracker.update("A", true)
        now += 5_000
        tracker.checkpoint()
        tracker.checkpoint() // Repeated callbacks must not double count.
        now += 2_000
        tracker.update("A", false) // Paused, buffering, or audio focus lost.
        now += 60_000
        tracker.checkpoint()
        assertEquals(7_000L, totals["A"])
        tracker.update("A", true)
        now += 1_000
        tracker.update("A", true) // A speed or seek event does not change the wall-time clock.
        now += 2_000
        tracker.update("B", true)
        now += 3_000
        tracker.stop()
        now += 60_000
        tracker.checkpoint()
        assertEquals(10_000L, totals["A"])
        assertEquals(3_000L, totals["B"])
    }

    @Test fun restartingTheServiceNeverCountsTimeWhileTheProcessWasDead() {
        var now = 0L
        var total = 0L
        fun newTracker() = ListeningTimeTracker({ now }) { _, duration -> total += duration }
        val first = newTracker()
        first.update("A", true)
        now += 5_000
        first.stop()
        now += 86_400_000
        val restored = newTracker()
        restored.checkpoint()
        now += 20_000
        restored.update("A", true)
        now += 2_000
        restored.stop()
        assertEquals(7_000L, total)
    }
}
