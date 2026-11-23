package com.bililisten.android.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ListeningForestTest {
    @Test fun eachFourTreesMergeExactlyAtTheThresholdAndRetainPartialProgress() {
        val before = ForestGrowth(TREE_LISTENING_MS - 1)
        assertEquals(0L, before.earnedTrees)
        assertEquals(1L, before.remainingMs)
        for ((units, stage) in listOf(1L to ForestTreeStage.SMALL, 4L to ForestTreeStage.MEDIUM,
            16L to ForestTreeStage.LARGE)) {
            val growth = ForestGrowth(units * TREE_LISTENING_MS)
            assertEquals(1L, growth.count(stage))
            assertEquals(TREE_LISTENING_MS, growth.remainingMs)
            assertEquals(0L, growth.growingMs)
            assertEquals(listOf(stage, ForestTreeStage.SEEDLING), ForestGrove.create(growth).trees().map { it.stage }.toList())
        }
        assertEquals(listOf(1, 2, 4), listOf(ForestTreeStage.SMALL, ForestTreeStage.MEDIUM,
            ForestTreeStage.LARGE).map { it.footprint })
        // A former ancient tree's time now yields four large trees, with no fourth tier.
        val previousAncient = ForestGrowth(64L * TREE_LISTENING_MS)
        assertEquals(4L, previousAncient.large)
        assertEquals(5L, ForestGrove.create(previousAncient).treeCount)
        val mixed = ForestGrowth(91L * TREE_LISTENING_MS + 12_345L)
        assertEquals(3, mixed.small)
        assertEquals(2, mixed.medium)
        assertEquals(5L, mixed.large)
        assertEquals(12_345L, mixed.growingMs)
    }

    @Test fun decomposingNeverLosesTimeIncludingVeryLargeTotals() {
        val totals = (0L..512L).map { it * TREE_LISTENING_MS + 12_345L } + listOf(-1L, 0L, Long.MAX_VALUE)
        totals.forEach { total ->
            val growth = ForestGrowth(total)
            val units = growth.small + 4L * growth.medium + 16L * growth.large
            assertEquals(total.coerceAtLeast(0), units * TREE_LISTENING_MS + growth.growingMs)
            assertTrue(growth.growingFraction in 0f..1f)
        }
    }

    @Test fun theWholeForestAccountsForEveryTreeAndDistantStandsNeverDiscardTrees() {
        for (large in listOf(0L, 1L, 3L, 4L, 7L, 25L, 200L)) {
            val growth = ForestGrowth((large * 16 + 15) * TREE_LISTENING_MS)
            val grove = ForestGrove.create(growth)
            val stages = grove.trees().map { it.stage }.toList()
            assertEquals(growth.treeCount, stages.size.toLong())
            for (stage in ForestTreeStage.entries) assertEquals(growth.count(stage), stages.count { it == stage }.toLong())
            for (span in listOf(1, 2, 3, 8)) assertEquals(large, grove.stands(span).sumOf { it.count })
        }
        val oldest = ForestGrove.create(ForestGrowth(Long.MAX_VALUE))
        val span = kotlin.math.ceil(kotlin.math.sqrt(oldest.large.toDouble()) / 64).toInt()
        val stands = oldest.stands(span).toList()
        assertTrue(stands.size < 5_000)
        assertEquals(oldest.large, stands.sumOf { it.count })
        assertEquals(ForestTreeStage.SEEDLING, oldest.treeAt(oldest.treeCount - 1).stage)
    }

    @Test fun layoutHasNoOverlappingFootprintsAndIsStableBetweenCheckpoints() {
        for (units in listOf(0L, 1L, 3L, 4L, 15L, 16L, 63L, 64L, 127L, 191L, 255L, 256L)) {
            val growth = ForestGrowth(units * TREE_LISTENING_MS)
            val grove = ForestGrove.create(growth)
            val cells = mutableSetOf<Pair<Int, Int>>()
            grove.trees().forEach { tree ->
                for (y in tree.y until tree.y + tree.stage.footprint) for (x in tree.x until tree.x + tree.stage.footprint) {
                    assertTrue(x in 0 until grove.width && y in 0 until grove.depth)
                    assertTrue("Two trees must not occupy the same tile", cells.add(x to y))
                    assertTrue(grove.isOccupied(x, y))
                }
            }
            assertEquals(grove, ForestGrove.create(ForestGrowth(growth.totalMs + 10_000)))
        }
    }

    @Test fun fixedSeasonOpeningDatesApplyEveryYearIncludingLeapYearsAndWinterAcrossNewYear() {
        for (year in listOf(2024, 2025, 2026, 2030)) {
            val starts = listOf(LocalDate.of(year, 2, 4) to ForestSeason.SPRING,
                LocalDate.of(year, 5, 5) to ForestSeason.SUMMER,
                LocalDate.of(year, 8, 7) to ForestSeason.AUTUMN,
                LocalDate.of(year, 11, 7) to ForestSeason.WINTER)
            var previous = ForestSeason.WINTER
            for ((date, season) in starts) {
                assertEquals("The day before $date", previous, ForestSeason.fromDate(date.minusDays(1)))
                assertEquals("The first day of $season", season, ForestSeason.fromDate(date))
                previous = season
            }
            assertEquals(ForestSeason.WINTER, ForestSeason.fromDate(LocalDate.of(year, 12, 31)))
            assertEquals(ForestSeason.WINTER, ForestSeason.fromDate(LocalDate.of(year + 1, 1, 1)))
        }
        assertEquals(ForestSeason.SPRING, ForestSeason.fromDate(LocalDate.of(2024, 2, 29)))
        assertEquals(ForestSeason.WINTER, ForestSeason.fromDate(LocalDate.of(2026, 11, 13)))
    }
}
