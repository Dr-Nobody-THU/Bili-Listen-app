package com.bililisten.android.data

import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.ceil
import kotlin.math.sqrt

internal const val TREE_LISTENING_MS = 24L * 60 * 60 * 1_000
internal const val SHARED_FOREST_SEED = 0x715348

internal enum class ForestSeason(val label: String) {
    SPRING("春 · 新芽"), SUMMER("夏 · 浓荫"), AUTUMN("秋 · 金叶"), WINTER("冬 · 落雪");

    companion object {
        // Fixed dates for the four season-opening solar terms, as a visual calendar convention.
        fun fromDate(date: LocalDate): ForestSeason {
            val day = MonthDay.from(date)
            return when {
                day < MonthDay.of(2, 4) -> WINTER
                day < MonthDay.of(5, 5) -> SPRING
                day < MonthDay.of(8, 7) -> SUMMER
                day < MonthDay.of(11, 7) -> AUTUMN
                else -> WINTER
            }
        }
    }
}

internal enum class ForestTreeStage(val label: String, val footprint: Int, val units: Int) {
    SEEDLING("幼苗", 1, 0), SMALL("小树", 1, 1), MEDIUM("中树", 2, 4),
    LARGE("大树", 4, 16),
}

/** A view of existing listening totals; merging never changes or rounds the saved time. */
internal data class ForestGrowth(val totalMs: Long) {
    val earnedTrees = totalMs.coerceAtLeast(0L) / TREE_LISTENING_MS
    val small = (earnedTrees % 4).toInt()
    val medium = (earnedTrees / 4 % 4).toInt()
    val large = earnedTrees / 16
    val growingMs = totalMs.coerceAtLeast(0L) % TREE_LISTENING_MS
    val growingFraction = growingMs.toFloat() / TREE_LISTENING_MS
    val remainingMs = TREE_LISTENING_MS - growingMs
    val treeCount = large + medium + small + 1L

    fun count(stage: ForestTreeStage): Long = when (stage) {
        ForestTreeStage.SEEDLING -> 1
        ForestTreeStage.SMALL -> small.toLong()
        ForestTreeStage.MEDIUM -> medium.toLong()
        ForestTreeStage.LARGE -> large
    }

}

internal data class ForestTree(val stage: ForestTreeStage, val x: Int, val y: Int, val variant: Int) {
    val centerX get() = x + stage.footprint / 2f
    val centerY get() = y + stage.footprint / 2f
}

/** An area of mature canopy, used only when individual trees become too small to distinguish. */
internal data class ForestStand(val x: Int, val y: Int, val width: Int, val depth: Int, val count: Long)

/** One continuous landscape. Large layouts are indexed rather than allocated tree by tree. */
@ConsistentCopyVisibility
internal data class ForestGrove private constructor(
    val width: Int,
    val depth: Int,
    val large: Long,
    val medium: Int,
    val small: Int,
    private val compactTrees: List<ForestTree>? = null,
) {
    val treeCount = large + medium + small + 1L
    private val columns get() = (width - 1) / 5
    private val rows get() = (depth - 1) / 5
    private val nurseryTrees by lazy { (large until treeCount).map(::treeAt) }

    // The backmost large tree and the nursery bound the height without walking a huge forest.
    val extentTrees: List<ForestTree> get() = compactTrees ?: (listOf(treeAt(0L)) + nurseryTrees)

    fun treeAt(index: Long): ForestTree {
        require(index in 0 until treeCount)
        compactTrees?.let { return it[index.toInt()] }
        if (index < large) return ForestTree(ForestTreeStage.LARGE,
            (index % columns).toInt() * 5 + 1, (index / columns).toInt() * 5 + 1,
            (forestNoise(SHARED_FOREST_SEED, index.hashCode(), 4) * 1000).toInt())
        val slot = (index - large).toInt()
        val (stage, position) = when {
            slot < medium -> ForestTreeStage.MEDIUM to listOf(0 to 0, 2 to 0, 0 to 2)[slot]
            slot < medium + small -> ForestTreeStage.SMALL to listOf(2 to 2, 3 to 2, 2 to 3)[slot - medium]
            else -> ForestTreeStage.SEEDLING to (3 to 3)
        }
        return ForestTree(stage, (columns - 1) * 5 + 1 + position.first, (rows - 1) * 5 + 1 + position.second,
            (forestNoise(SHARED_FOREST_SEED, slot, stage.footprint) * 1000).toInt())
    }

    fun trees(): Sequence<ForestTree> = (0L until treeCount).asSequence().map(::treeAt)

    fun isOccupied(x: Int, y: Int): Boolean {
        compactTrees?.let { trees -> return trees.any { it.contains(x, y) } }
        if (x !in 1 until width - 1 || y !in 1 until depth - 1) return false
        if ((x - 1) % 5 == 4 || (y - 1) % 5 == 4) return false
        val index = (y - 1).toLong() / 5 * columns + (x - 1) / 5
        return index < large || nurseryTrees.any { it.contains(x, y) }
    }

    /** Every mature tree contributes exactly once, even at a scale of less than one pixel. */
    fun stands(cellSpan: Int): Sequence<ForestStand> = sequence {
        require(cellSpan > 0)
        compactTrees?.let { trees ->
            trees.filter { it.stage == ForestTreeStage.LARGE }.forEach {
                yield(ForestStand(it.x, it.y, 4, 4, 1))
            }
            return@sequence
        }
        val fullRows = (large / columns).toInt()
        val remainder = (large % columns).toInt()
        for (row in 0 until rows step cellSpan) for (column in 0 until columns step cellSpan) {
            val endRow = minOf(row + cellSpan, rows)
            val endColumn = minOf(column + cellSpan, columns)
            val wholeRows = (minOf(endRow, fullRows) - row).coerceAtLeast(0)
            val partialColumns = if (fullRows in row until endRow) (minOf(endColumn, remainder) - column).coerceAtLeast(0) else 0
            if (wholeRows > 0) {
                yield(ForestStand(column * 5 + 1, row * 5 + 1, (endColumn - column) * 5 - 1, wholeRows * 5 - 1,
                    wholeRows.toLong() * (endColumn - column)))
            }
            if (partialColumns > 0) {
                yield(ForestStand(column * 5 + 1, fullRows * 5 + 1, partialColumns * 5 - 1, 4, partialColumns.toLong()))
            }
        }
    }

    companion object {
        fun create(growth: ForestGrowth): ForestGrove {
            if (growth.large > 3) {
                val cells = growth.large + 1 // Reserve a front corner for the smaller trees.
                val columns = ceil(sqrt(cells.toDouble())).toInt()
                val rows = ((cells + columns - 1) / columns).toInt()
                return ForestGrove(columns * 5 + 1, rows * 5 + 1, growth.large, growth.medium, growth.small)
            }
            val stages = buildList {
                repeat(growth.large.toInt()) { add(ForestTreeStage.LARGE) }
                repeat(growth.medium) { add(ForestTreeStage.MEDIUM) }
                repeat(growth.small) { add(ForestTreeStage.SMALL) }
                add(ForestTreeStage.SEEDLING)
            }
            var side = ceil(sqrt(stages.sumOf { it.footprint * it.footprint } * 1.45)).toInt().coerceAtLeast(3)
            while (true) {
                val placed = mutableListOf<ForestTree>()
                val used = Array(side) { BooleanArray(side) }
                for ((index, stage) in stages.withIndex()) {
                    val width = stage.footprint
                    val candidates = buildList {
                        for (y in 0..side - width) for (x in 0..side - width) {
                            if ((y until y + width).all { row -> (x until x + width).all { col -> !used[row][col] } }) add(x to y)
                        }
                    }
                    // Tall trees stay toward the back; smaller trees and the growing seedling remain visible in front.
                    val target = side * when (stage) {
                        ForestTreeStage.LARGE -> .28f
                        ForestTreeStage.MEDIUM -> .64f
                        ForestTreeStage.SMALL -> .78f
                        ForestTreeStage.SEEDLING -> .9f
                    }
                    val position = candidates.minByOrNull { (x, y) ->
                        val dx = x + width / 2f - target
                        val dy = y + width / 2f - target
                        dx * dx + dy * dy + forestNoise(SHARED_FOREST_SEED, x, y) * .8f
                    } ?: break
                    val (x, y) = position
                    for (row in y until y + width) for (col in x until x + width) used[row][col] = true
                    placed.add(ForestTree(stage, x, y, (forestNoise(SHARED_FOREST_SEED, index, width) * 1000).toInt()))
                }
                if (placed.size == stages.size) return ForestGrove(side, side, growth.large, growth.medium, growth.small, placed)
                side++
            }
        }
    }
}

private fun ForestTree.contains(x: Int, y: Int) =
    x in this.x until this.x + stage.footprint && y in this.y until this.y + stage.footprint

internal fun forestNoise(seed: Int, x: Int, y: Int): Float {
    var hash = seed xor (x * 374761393) xor (y * 668265263)
    hash = (hash xor (hash ushr 13)) * 1274126177
    return ((hash xor (hash ushr 16)).toLong() and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE
}
