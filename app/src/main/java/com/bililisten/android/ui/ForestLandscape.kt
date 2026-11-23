package com.bililisten.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bililisten.android.data.*
import kotlin.math.*

private const val TAU = (Math.PI * 2).toFloat()

private data class WoodlandColors(
    val soil: Color, val grass: Color, val moss: Color, val leaf: Color,
    val light: Color, val shade: Color, val flower: Color, val water: Color,
)

private fun woodlandColors(season: ForestSeason) = when (season) {
    ForestSeason.SPRING -> WoodlandColors(Color(0xFF353D35), Color(0xFF526B51), Color(0xFF809166),
        Color(0xFF659D6F), Color(0xFFBCDB98), Color(0xFF285D4D), Color(0xFFF3BACB), Color(0xFF578C8A))
    ForestSeason.SUMMER -> WoodlandColors(Color(0xFF293B34), Color(0xFF3F5F4C), Color(0xFF718B59),
        Color(0xFF3F8667), Color(0xFFA1CE85), Color(0xFF1D5145), Color(0xFFE8DB9F), Color(0xFF428D92))
    ForestSeason.AUTUMN -> WoodlandColors(Color(0xFF46392F), Color(0xFF6D6045), Color(0xFFAA8C5E),
        Color(0xFFCA8B4D), Color(0xFFF0C981), Color(0xFF84503B), Color(0xFFE5AE79), Color(0xFF568684))
    ForestSeason.WINTER -> WoodlandColors(Color(0xFF3C5055), Color(0xFF829B9F), Color(0xFFB8CCCC),
        Color(0xFF5F8587), Color(0xFFBAD7D6), Color(0xFF345B65), Color(0xFFE3F0EE), Color(0xFF8CAFB7))
}

private fun project(x: Float, y: Float) = Offset((x - y) * 24f, (x + y) * 12f)
private fun ForestTree.height() = when (stage) {
    ForestTreeStage.SEEDLING -> 28f
    ForestTreeStage.SMALL -> 48f
    ForestTreeStage.MEDIUM -> 78f
    ForestTreeStage.LARGE -> 150f
}
private fun ForestTree.width() = when (stage) {
    ForestTreeStage.SEEDLING -> 18f
    ForestTreeStage.SMALL -> 32f
    ForestTreeStage.MEDIUM -> 54f
    ForestTreeStage.LARGE -> 112f
}

private data class PaintedLayer(val image: ImageBitmap, val origin: Offset, val resolution: Float)
private data class PaintedTree(val tree: ForestTree, val trunk: PaintedLayer?, val leaves: PaintedLayer?)

internal data class ForestViewport(val bounds: Rect, val zoom: Float, val origin: Offset)

internal fun forestViewport(grove: ForestGrove, viewport: Size): ForestViewport {
    val minY = minOf(-18f, grove.extentTrees.minOf { project(it.centerX, it.centerY).y - it.height() - 12 })
    val bounds = Rect(-grove.depth * 24f - 18, minY, grove.width * 24f + 18, (grove.width + grove.depth) * 12f + 40)
    val zoom = minOf(viewport.width / bounds.width, viewport.height / bounds.height)
    val origin = Offset((viewport.width - bounds.width * zoom) / 2 - bounds.left * zoom,
        (viewport.height - bounds.height * zoom) / 2 - bounds.top * zoom)
    return ForestViewport(bounds, zoom, origin)
}

/** Artwork is rasterized on size/season/grove changes, then only composited during wind animation. */
@Composable
internal fun ForestCanvas(grove: ForestGrove, progress: Float, season: ForestSeason, seed: Int, modifier: Modifier) {
    val palette = remember(season) { woodlandColors(season) }
    val glow = MaterialTheme.colorScheme.primary
    val time = rememberForestTime()
    val growing = rememberUpdatedState(progress)
    val painting = remember(grove, palette, season, seed, glow, time, growing) {
        Modifier.drawWithCache {
            if (size.width <= 0 || size.height <= 0) return@drawWithCache onDrawBehind {}
            val (bounds, zoom, origin) = forestViewport(grove, size)
            // Raster size follows the screen, never the number of trees or the area of the forest.
            val resolution = (zoom * 1.1f).coerceAtMost(4f)
            val detailed = 112f * zoom >= 12.dp.toPx()
            val visibleTrees = if (detailed) grove.trees().sortedBy { it.centerX + it.centerY }.toList() else emptyList()
            val pool = if (grove.large > 3) project(.6f, grove.depth * .6f) else {
                val openCells = buildList {
                    for (y in 0 until grove.depth) for (x in 0 until grove.width) {
                        if (!grove.isOccupied(x, y)) add(x to y)
                    }
                }
                openCells.minByOrNull { (x, y) -> (x - grove.width * .12f).pow(2) + (y - grove.depth * .6f).pow(2) }
                    ?.let { (x, y) -> project(x + .5f, y + .5f) }
            }
            val land = paintLayer(bounds, resolution) { paintTerrain(grove, visibleTrees, palette, season, seed, pool, zoom) }
            val distantCanopy = if (detailed) null else paintLayer(bounds, resolution) { paintDistantForest(grove, palette, season, zoom) }
            val distantSeed = if (detailed) null else grove.extentTrees.first { it.stage == ForestTreeStage.SEEDLING }
            val atlas = mutableMapOf<Pair<ForestTreeStage, Int>, Pair<PaintedLayer, PaintedLayer>>()
            val trees = visibleTrees.map { tree ->
                if (tree.stage == ForestTreeStage.SEEDLING) PaintedTree(tree, null, null)
                else {
                    val variant = if (grove.treeCount > 40) tree.variant % 8 * 113 else tree.variant
                    val (trunk, leaves) = atlas.getOrPut(tree.stage to variant) {
                        val artwork = tree.copy(variant = variant)
                        val treeBounds = Rect(-tree.width() * .72f, -tree.height() - 10, tree.width() * .72f, 8f)
                        paintLayer(treeBounds, resolution) { paintTrunk(artwork, palette, season) } to
                            paintLayer(treeBounds, resolution) { paintFoliage(artwork, palette, season) }
                    }
                    PaintedTree(tree, trunk, leaves)
                }
            }
            onDrawBehind {
                val t = time.value
                val airBounds = Rect(0f, 0f, size.width / density, size.height / density)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .075f), Color.Transparent), center,
                    size.minDimension * .65f), size.minDimension * .65f)
                scale(density, density, Offset.Zero) { paintAir(season, palette, seed, airBounds, t, foreground = false) }
                translate(origin.x, origin.y) {
                    scale(zoom, zoom, Offset.Zero) {
                        drawLayer(land)
                        pool?.let { paintWaterMotion(it, palette, season, t) }
                        distantCanopy?.let { drawLayer(it) }
                        distantSeed?.let { tree ->
                            val p = project(tree.centerX, tree.centerY)
                            translate(p.x, p.y) { paintSeedling(growing.value, palette, t) }
                        }
                        trees.forEach { sprite ->
                            val position = project(sprite.tree.centerX, sprite.tree.centerY)
                            val phase = sprite.tree.variant / 1000f * TAU
                            val wind = sin(t * TAU / 8 + phase) + .35f * sin(t * TAU / 12 + phase * 2)
                            translate(position.x, position.y) {
                                if (sprite.tree.stage == ForestTreeStage.SEEDLING) {
                                    rotate(wind * 3.5f, Offset.Zero) { paintSeedling(growing.value, palette, t) }
                                } else {
                                    drawLayer(sprite.trunk!!)
                                    val amplitude = if (sprite.tree.stage == ForestTreeStage.LARGE) .95f else 1.65f
                                    rotate(wind * amplitude, Offset(0f, -sprite.tree.height() * .3f)) { drawLayer(sprite.leaves!!) }
                                }
                            }
                        }
                    }
                }
                scale(density, density, Offset.Zero) { paintAir(season, palette, seed, airBounds, t, foreground = true) }
            }
        }
    }
    Box(modifier.testTag("forest_canvas").clipToBounds().then(painting))
}

private fun CacheDrawScope.paintLayer(bounds: Rect, resolution: Float, paint: DrawScope.() -> Unit): PaintedLayer {
    val image = ImageBitmap(ceil(bounds.width * resolution).toInt().coerceAtLeast(1), ceil(bounds.height * resolution).toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(this, layoutDirection, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
        scale(resolution, resolution, Offset.Zero) {
            translate(-bounds.left, -bounds.top) { paint() }
        }
    }
    return PaintedLayer(image, bounds.topLeft, resolution)
}

private fun DrawScope.drawLayer(layer: PaintedLayer) {
    translate(layer.origin.x, layer.origin.y) {
        scale(1f / layer.resolution, 1f / layer.resolution, Offset.Zero) {
            drawImage(layer.image)
        }
    }
}

private fun DrawScope.paintTerrain(grove: ForestGrove, trees: List<ForestTree>, c: WoodlandColors, season: ForestSeason,
    seed: Int, pool: Offset?, zoom: Float) {
    val side = maxOf(grove.width, grove.depth).toFloat()
    val width = grove.width.toFloat()
    val depth = grove.depth.toFloat()
    val corners = listOf(project(0f, 0f), project(width, 0f), project(width, depth), project(0f, depth))
    val rim = roundedPolygon(corners, .07f)
    val front = corners[2]
    translate(front.x / 2, front.y - (width + depth) * 1.5f) {
        scale((width + depth) * 12, (width + depth) * 1.5f + 30, Offset.Zero) {
            drawCircle(Brush.radialGradient(listOf(Color(0x45030C12), Color.Transparent), Offset.Zero, 1f), 1f, Offset.Zero)
        }
    }
    val underside = roundedPolygon(corners.map { Offset(it.x * .94f, it.y + 16f) }, .12f)
    drawPath(underside, Brush.verticalGradient(listOf(c.soil, Color(0xFF15272A)), startY = side * 8, endY = front.y + 20))
    for ((index, edge) in listOf(corners[3] to front, front to corners[1]).withIndex()) {
        val bottomA = Offset(edge.first.x * .94f, edge.first.y + 16)
        val bottomB = Offset(edge.second.x * .94f, edge.second.y + 16)
        val face = roundedPolygon(listOf(edge.first, edge.second, bottomB, bottomA), .025f)
        drawPath(face,
            Brush.verticalGradient(listOf(lerp(c.soil, c.moss, if (index == 0) .22f else .1f), c.soil),
                side * 12, front.y + 16))
        clipPath(face) {
            val steps = (side.toInt() * 5).coerceAtMost(250)
            repeat(steps) { detail ->
                val fraction = (detail + .5f) / steps
                val p = lerp(edge.first, edge.second, fraction)
                val n = forestNoise(seed, detail, if (index == 0) 19 else 29)
                drawOval(lerp(c.soil, c.moss, .16f + n * .14f),
                    p + Offset(-2f, 4f + n * 8), Size(2f + n * 3, .8f + n))
            }
        }
    }
    drawPath(rim, Brush.linearGradient(listOf(lerp(c.grass, c.moss, .3f), c.grass, lerp(c.grass, c.soil, .2f)),
        Offset(-side * 12, 0f), front))
    clipPath(rim) {
        repeat((grove.width.toLong() * grove.depth * 14).coerceAtMost(6_000).toInt()) { index ->
            val x = forestNoise(seed, index, 40) * width
            val y = forestNoise(seed, index, 41) * depth
            val p = project(x, y)
            val n = forestNoise(seed, index, 42)
            drawOval((if (n > .45f) c.moss else c.soil).copy(alpha = .12f + n * .18f),
                p, Size(1.2f + n * 4, .6f + n))
        }
        val groundStep = ceil(side / 64f).toInt().coerceAtLeast(1)
        if (zoom >= .15f) for (y in 0 until grove.depth step groundStep) for (x in 0 until grove.width step groundStep) {
            val p = project(x + .5f, y + .5f)
            if (!grove.isOccupied(x, y) && (pool == null || (p - pool).getDistance() > 20)) {
                val n = forestNoise(seed, x, y)
                if (n > .76f) paintStone(p, 3.5f + n * 3, c, season)
                else paintUnderstory(p, c, season, (n * 10000).toInt())
            }
        }
        pool?.let { paintPool(it, c, season) }
    }
    drawPath(rim, c.moss.copy(alpha = .55f), style = Stroke(1.3f))
    trees.forEach { tree ->
        val p = project(tree.centerX, tree.centerY)
        val w = tree.width()
        drawOval(Brush.radialGradient(listOf(Color(0x4D0D2625), Color.Transparent), p + Offset(w * .15f, 2f), w * .6f),
            p + Offset(-w * .55f, -w * .11f), Size(w * 1.2f, w * .35f))
        if (tree.stage == ForestTreeStage.SEEDLING) {
            drawOval(c.soil, p + Offset(-8f, -3f), Size(16f, 7f))
            drawOval(c.moss.copy(alpha = .6f), p + Offset(-8f, -3f), Size(16f, 7f), style = Stroke(.8f))
        } else {
            repeat(7) { index ->
                val n = forestNoise(tree.variant, index, 2)
                val root = p + Offset(cos(index * TAU / 7) * w * .17f, sin(index * TAU / 7) * w * .045f)
                drawOval(c.moss.copy(alpha = .65f), root, Size(3f + n * 3, 1.5f))
            }
        }
    }
}

/** At a distance, render all mature trees into a screen-sized canopy layer instead of thousands of sprites. */
private fun DrawScope.paintDistantForest(grove: ForestGrove, c: WoodlandColors, season: ForestSeason, zoom: Float) {
    val span = ceil(3f / (120f * zoom)).toInt().coerceAtLeast(1)
    grove.stands(span).sortedBy { it.x + it.y + (it.width + it.depth) / 2 }.forEach { stand ->
        val n = forestNoise(SHARED_FOREST_SEED, stand.x, stand.y)
        val leaf = lerp(c.leaf, c.light, .08f + n * .2f)
        if (stand.count == 1L) {
            val p = project(stand.x + 2f, stand.y + 2f)
            drawOval(c.shade.copy(alpha = .3f), p - Offset(48f, 9f), Size(100f, 28f))
            drawLine(Color(0xFFA8A18A), p, p - Offset(0f, 100f), 9f, StrokeCap.Round)
            drawOval(c.shade, p - Offset(56f, 145f), Size(112f, 100f))
            drawOval(leaf, p - Offset(52f, 150f), Size(98f, 87f))
            drawOval(lerp(leaf, c.light, .2f), p - Offset(35f, 151f), Size(63f, 50f))
            if (season == ForestSeason.WINTER) drawOval(Color(0xFFDAEAE7), p - Offset(44f, 149f), Size(84f, 28f))
        } else {
            val corners = listOf(project(stand.x.toFloat(), stand.y.toFloat()),
                project((stand.x + stand.width).toFloat(), stand.y.toFloat()),
                project((stand.x + stand.width).toFloat(), (stand.y + stand.depth).toFloat()),
                project(stand.x.toFloat(), (stand.y + stand.depth).toFloat()))
            drawPath(roundedPolygon(corners, .05f), c.shade.copy(alpha = .4f))
            val crown = corners.map { it - Offset(0f, 105f) }
            val color = if (season == ForestSeason.WINTER) lerp(leaf, Color(0xFFDBEAE7), .55f) else leaf
            drawPath(roundedPolygon(crown, .09f), color)
            drawLine(c.light.copy(alpha = .35f), crown[0], crown[3], 1f / zoom, StrokeCap.Round)
        }
    }
    // The small nursery remains in the same front corner at every overview scale.
    grove.extentTrees.filter { it.stage == ForestTreeStage.SMALL || it.stage == ForestTreeStage.MEDIUM }.forEach { tree ->
        val p = project(tree.centerX, tree.centerY)
        translate(p.x, p.y) {
            paintTrunk(tree, c, season)
            paintFoliage(tree, c, season)
        }
    }
}

private fun DrawScope.paintUnderstory(p: Offset, c: WoodlandColors, season: ForestSeason, seed: Int) {
    repeat(6) { index ->
        val n = forestNoise(seed, index, 4)
        val root = p + Offset((n - .5f) * 9, forestNoise(seed, index, 5) * 3)
        val tip = root + Offset((n - .3f) * 5, -2f - n * 4)
        drawPath(Path().apply { moveTo(root.x, root.y); quadraticTo(root.x - 1f, tip.y, tip.x, tip.y) },
            lerp(c.grass, c.moss, .5f + n * .5f), style = Stroke(.65f, cap = StrokeCap.Round))
        if (n > .6f && season != ForestSeason.WINTER) {
            val color = if (season == ForestSeason.AUTUMN) c.leaf else c.flower
            drawCircle(color.copy(alpha = .9f), .95f, tip)
            drawCircle(lerp(color, Color.White, .5f), .35f, tip + Offset(-.25f, -.25f))
        }
    }
    if (seed % 5 == 0 && season != ForestSeason.WINTER) {
        // A tiny pair of woodland mushrooms, kept well below tree size.
        for (i in 0..1) {
            val stem = p + Offset(3f + i * 3f, -i.toFloat())
            drawLine(Color(0xFFC3C3A7), stem, stem - Offset(0f, 3f), 1f)
            drawOval(Color(0xFFB98C70), stem - Offset(2f, 4f), Size(4f, 2f))
            drawCircle(Color(0xFFE0CBB1), .4f, stem - Offset(.5f, 3.5f))
        }
    }
}

private fun DrawScope.paintStone(p: Offset, r: Float, c: WoodlandColors, season: ForestSeason) {
    drawOval(Color(0x250D2020), p + Offset(-r, 0f), Size(r * 2.4f, r * .65f))
    val outline = organicCloud(p - Offset(0f, r * .25f), r, r * .6f, r.toInt())
    drawPath(outline, Brush.linearGradient(listOf(lerp(c.moss, Color(0xFFB1BCB3), .35f), c.soil),
        p - Offset(r, r), p + Offset(r, r * .5f)))
    drawLine(c.moss.copy(alpha = .55f), p - Offset(r * .6f, r * .45f), p + Offset(r * .1f, -r * .6f), .65f, StrokeCap.Round)
    if (season == ForestSeason.WINTER) drawOval(Color(0xFFD9E7E3), p - Offset(r * .65f, r * .7f), Size(r * 1.3f, r * .35f))
}

private fun DrawScope.paintPool(p: Offset, c: WoodlandColors, season: ForestSeason) {
    drawOval(c.soil.copy(alpha = .7f), p - Offset(18f, 9f), Size(36f, 18f))
    drawOval(Brush.linearGradient(listOf(lerp(c.water, c.shade, .5f), c.water), p - Offset(6f, 8f), p + Offset(4f, 6f)),
        p - Offset(15f, 7f), Size(30f, 14f))
    drawArc(c.light.copy(alpha = .5f), 195f, 115f, false, p - Offset(16f, 7f), Size(32f, 14f), style = Stroke(1.3f, cap = StrokeCap.Round))
    for (i in 0..3) paintStone(p + Offset(-13f + i * 8f, 6f + sin(i.toFloat()) * 2), 2f, c, season)
    if (season == ForestSeason.WINTER) {
        drawLine(Color(0xFFDBEBEB).copy(alpha = .65f), p - Offset(8f, 3f), p + Offset(4f, 1f), .6f)
        drawLine(Color(0xFFDBEBEB).copy(alpha = .4f), p + Offset(4f, 1f), p + Offset(9f, -3f), .6f)
    }
}

private fun DrawScope.paintWaterMotion(p: Offset, c: WoodlandColors, season: ForestSeason, time: Float) {
    if (season == ForestSeason.WINTER) return
    repeat(2) { i ->
        val f = (time / 8 + i * .5f) % 1f
        val r = 2f + f * 11f
        drawOval(c.light.copy(alpha = sin(f * Math.PI).toFloat() * .28f), p - Offset(r, r * .35f),
            Size(r * 2, r * .7f), style = Stroke(.55f))
    }
}

private fun DrawScope.paintTrunk(tree: ForestTree, c: WoodlandColors, season: ForestSeason) {
    val h = tree.height()
    val w = tree.width()
    val r = w * if (tree.stage == ForestTreeStage.LARGE) .095f else .065f
    val bend = (tree.variant % 7 - 3) * w * .017f
    val bark = if (season == ForestSeason.WINTER) Color(0xFFA6ADA1) else Color(0xFFB9A17C)
    val trunk = Path().apply {
        moveTo(-r * 1.7f, 2f)
        cubicTo(-r * .6f, -h * .08f, -r * .9f + bend, -h * .3f, bend - r * .5f, -h * .55f)
        lineTo(bend + r * .35f, -h * .8f)
        cubicTo(r * .5f + bend, -h * .35f, r * .3f, -h * .13f, r * 1.4f, 1f)
        quadraticTo(0f, 5f, -r * 1.7f, 2f)
        close()
    }
    drawPath(trunk, Brush.horizontalGradient(listOf(Color(0xFF4F5142), bark, Color(0xFF70644F)), -r * 1.2f, r))
    repeat(if (tree.stage == ForestTreeStage.LARGE) 8 else 5) { i ->
        val direction = if (i % 2 == 0) -1 else 1
        val height = h * (.32f + i * .055f)
        val reach = w * (.25f - i * .014f) * direction
        val branch = Path().apply {
            moveTo(bend * .3f, -height + h * .12f)
            cubicTo(reach * .25f, -height + h * .05f, reach * .6f, -height - h * .03f, reach, -height - h * .2f)
        }
        drawPath(branch, lerp(bark, Color(0xFF515747), .35f), style = Stroke((r * .6f - i * .15f).coerceAtLeast(.6f), cap = StrokeCap.Round))
    }
    clipPath(trunk) {
        repeat(9) { i ->
            val n = forestNoise(tree.variant, i, 8)
            val x = (n - .5f) * r * 1.5f
            drawPath(Path().apply { moveTo(x, 0f); cubicTo(x - r * .25f, -h * .12f, x + bend, -h * .32f, x + bend, -h * .62f) },
                Color(0xFF474C3C).copy(alpha = .25f + n * .2f), style = Stroke(.5f + n * .5f))
        }
        drawOval(c.moss.copy(alpha = .35f), Offset(-r * 1.2f, -h * .13f), Size(r * 1.5f, h * .15f))
    }
    if (tree.stage == ForestTreeStage.LARGE) {
        drawOval(Color(0xFF515442), Offset(-r * .35f, -h * .24f), Size(r * .6f, r * .95f))
        drawArc(bark.copy(alpha = .7f), -80f, 235f, false, Offset(-r * .45f, -h * .25f), Size(r * .8f, r * 1.2f), style = Stroke(.7f))
        for (i in -1..1) drawPath(Path().apply {
            moveTo(i * r * .5f, -r * 1.2f)
            quadraticTo(i * r, 0f, i * r * 2.1f, 3f)
        }, bark.copy(alpha = .8f), style = Stroke(r * .3f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.paintFoliage(tree: ForestTree, c: WoodlandColors, season: ForestSeason) {
    if (tree.variant % 3 == 0 && tree.stage != ForestTreeStage.LARGE) {
        paintCedar(tree, c, season)
        return
    }
    val h = tree.height()
    val w = tree.width()
    val leaf = if (season == ForestSeason.AUTUMN && tree.variant % 3 == 0) Color(0xFFB87550) else c.leaf
    val clusters = listOf(
        floatArrayOf(.24f, -.67f, .29f, .22f), floatArrayOf(-.23f, -.63f, .32f, .23f),
        floatArrayOf(.15f, -.82f, .25f, .17f), floatArrayOf(-.13f, -.82f, .3f, .18f),
        floatArrayOf(.04f, -.62f, .34f, .22f), floatArrayOf(-.31f, -.55f, .2f, .16f),
        floatArrayOf(.32f, -.54f, .22f, .16f),
    )
    clusters.forEachIndexed { index, shape ->
        val center = Offset(w * shape[0], h * shape[1])
        val rx = w * shape[2]
        val ry = h * shape[3]
        val cloud = organicCloud(center, rx, ry, tree.variant + index * 13)
        val main = lerp(leaf, c.shade, if (index == 0 || index == 6) .2f else .04f)
        drawPath(cloud, Brush.radialGradient(listOf(lerp(main, c.light, .45f), main, lerp(main, c.shade, .55f)),
            center - Offset(rx * .45f, ry * .6f), maxOf(rx, ry) * 1.8f))
        clipPath(cloud) {
            repeat(if (tree.stage == ForestTreeStage.SMALL) 24 else 48) { detail ->
                val n = forestNoise(tree.variant + index, detail, 10)
                val a = forestNoise(tree.variant + index, detail, 11) * TAU
                val radius = sqrt(forestNoise(tree.variant + index, detail, 12))
                val point = center + Offset(cos(a) * rx * radius, sin(a) * ry * radius)
                val lightAmount = ((1f - radius * sin(a)) * .5f).coerceIn(0f, 1f)
                val color = lerp(c.shade, c.light, lightAmount).copy(alpha = .2f + n * .4f)
                paintLeaf(point, 1.1f + w * (.018f + n * .013f), .48f, color, a * 57.3f - 40)
            }
            if (season == ForestSeason.WINTER && index != 0 && index != 6) {
                val cap = Path().apply {
                    moveTo(center.x - rx, center.y - ry * .18f)
                    cubicTo(center.x - rx * .6f, center.y - ry * 1.2f, center.x + rx * .6f, center.y - ry * 1.2f, center.x + rx, center.y - ry * .1f)
                    cubicTo(center.x + rx * .6f, center.y - ry * .35f, center.x + rx * .4f, center.y + ry * .06f, center.x + rx * .12f, center.y - ry * .12f)
                    cubicTo(center.x - rx * .3f, center.y + ry * .05f, center.x - rx * .55f, center.y - ry * .25f, center.x - rx, center.y - ry * .18f)
                    close()
                }
                drawPath(cap, Brush.verticalGradient(listOf(Color(0xFFE4EFEB), Color(0xFFB6D0CF)), center.y - ry, center.y))
            }
        }
        if (season == ForestSeason.SPRING && index % 2 == 1) repeat(3) { blossom ->
            val p = center + Offset((forestNoise(tree.variant, blossom, index) - .5f) * rx, (forestNoise(tree.variant, blossom, index + 7) - .5f) * ry)
            paintBlossom(p, w * .02f + .65f, c.flower)
        }
    }
}

private fun DrawScope.paintCedar(tree: ForestTree, c: WoodlandColors, season: ForestSeason) {
    val h = tree.height()
    val w = tree.width() * .9f
    val leaf = if (season == ForestSeason.AUTUMN) lerp(c.leaf, Color(0xFF82906A), .45f) else c.leaf
    for (tier in 0..6) {
        val bottom = -h * (.26f + tier * .095f)
        val tip = bottom - h * .24f
        val r = w * (.53f - tier * .064f)
        val outline = Path().apply {
            moveTo(0f, tip)
            cubicTo(-r * .25f, tip + h * .16f, -r * .7f, bottom - h * .03f, -r, bottom)
            quadraticTo(-r * .6f, bottom + h * .055f, 0f, bottom + h * .06f)
            quadraticTo(r * .6f, bottom + h * .055f, r, bottom)
            cubicTo(r * .65f, bottom - h * .06f, r * .2f, tip + h * .1f, 0f, tip)
            close()
        }
        drawPath(outline, Brush.linearGradient(listOf(lerp(leaf, c.light, .35f), leaf, c.shade), Offset(-r, tip), Offset(r, bottom)))
        clipPath(outline) {
            repeat(24) { i ->
                val n = forestNoise(tree.variant + tier, i, 18)
                val x = (n - .5f) * r * 2
                val y = bottom - forestNoise(tree.variant, i, tier) * h * .15f
                drawLine(c.light.copy(alpha = .26f), Offset(x, y), Offset(x + sign(x) * 2.3f, y + 1.6f), .65f, StrokeCap.Round)
            }
            if (season == ForestSeason.WINTER) {
                drawPath(Path().apply {
                    moveTo(-r, bottom)
                    quadraticTo(-r * .4f, bottom - h * .08f, 0f, bottom - h * .13f)
                    quadraticTo(r * .5f, bottom - h * .07f, r, bottom)
                    quadraticTo(0f, bottom + h * .025f, -r, bottom)
                    close()
                }, Color(0xFFDCEBE7))
            }
        }
    }
}

private fun DrawScope.paintSeedling(progress: Float, c: WoodlandColors, time: Float) {
    if (progress < .001f) {
        drawOval(Color(0xFFBCA986), Offset(-2.5f, -2f), Size(5f, 3f))
        drawLine(Color(0xFFE1D4B0), Offset(-1.5f, -1.4f), Offset(.8f, -1.8f), .6f)
        return
    }
    val h = 11 + progress * 16
    drawPath(Path().apply { moveTo(0f, 0f); quadraticTo(-2f, -h * .4f, 0f, -h * .9f) }, c.leaf, style = Stroke(1.4f, cap = StrokeCap.Round))
    paintLeaf(Offset(-4f, -h * .58f), 6f, .45f, c.light, -155f)
    paintLeaf(Offset(4f, -h * .85f), 6.5f, .42f, c.leaf, -30f)
    val f = (.5f + sin(time * TAU / 8) * .5f)
    drawCircle(Brush.radialGradient(listOf(c.light.copy(alpha = .12f * f), Color.Transparent), Offset(0f, -h * .6f), 12f), 12f, Offset(0f, -h * .6f))
}

private fun DrawScope.paintLeaf(p: Offset, length: Float, breadth: Float, color: Color, angle: Float) {
    rotate(angle, p) {
        drawPath(Path().apply {
            moveTo(p.x - length, p.y)
            cubicTo(p.x - length * .3f, p.y - length * breadth, p.x + length * .6f, p.y - length * breadth, p.x + length, p.y)
            cubicTo(p.x + length * .3f, p.y + length * breadth, p.x - length * .6f, p.y + length * breadth, p.x - length, p.y)
            close()
        }, color)
    }
}

private fun DrawScope.paintBlossom(p: Offset, r: Float, color: Color) {
    repeat(5) { i ->
        val a = i * TAU / 5
        drawCircle(color, r * .65f, p + Offset(cos(a), sin(a)) * r * .7f)
    }
    drawCircle(Color(0xFFFFE6B2), r * .3f, p)
}

private fun DrawScope.paintAir(season: ForestSeason, c: WoodlandColors, seed: Int, bounds: Rect, time: Float, foreground: Boolean) {
    val count = if (season == ForestSeason.WINTER) 26 else 15
    repeat(count) { index ->
        if ((index % 3 != 0) != foreground) return@repeat
        val n = forestNoise(seed, index, 101)
        val life = when (index % 4) { 0 -> 8f; 1 -> 12f; 2 -> 15f; else -> 20f }
        val f = (time / life + n) % 1f
        val opacity = sin(f * Math.PI).toFloat().coerceAtLeast(0f)
        val x = bounds.left + bounds.width * (.12f + forestNoise(seed, index, 102) * .76f) + sin(time * TAU / 12 + n * TAU) * 8
        val y = bounds.top + 10 + f * (bounds.height - 40)
        val p = Offset(x, y)
        when (season) {
            ForestSeason.SUMMER -> {
                val point = Offset(x, bounds.top + bounds.height * (.25f + n * .6f) + sin(time * TAU / 8 + n * TAU) * 8)
                val pulse = (.2f + .8f * (.5f + .5f * sin(time * TAU / 6 + index))).coerceIn(0f, 1f)
                val glow = Color(0xFFDCECB1)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = pulse * .3f), Color.Transparent), point, 5f), 5f, point)
                drawCircle(glow.copy(alpha = pulse * .85f), .65f + n * .45f, point)
            }
            ForestSeason.WINTER -> {
                drawCircle(Color(0xFFE6F1F0).copy(alpha = opacity * (.35f + n * .5f)), .55f + n, p)
                if (index % 5 == 0) {
                    drawLine(c.flower.copy(alpha = opacity * .45f), p - Offset(1.7f, 0f), p + Offset(1.7f, 0f), .5f)
                    drawLine(c.flower.copy(alpha = opacity * .45f), p - Offset(0f, 1.7f), p + Offset(0f, 1.7f), .5f)
                }
            }
            ForestSeason.AUTUMN -> {
                val angle = time * 360f / 8 + index * 53
                paintLeaf(p, 1.7f + n * 1.2f, .45f, lerp(c.light, c.leaf, n).copy(alpha = opacity * .75f), angle)
            }
            ForestSeason.SPRING -> {
                if (index < 2) {
                    val butterfly = Offset(x, bounds.top + bounds.height * (.3f + n * .45f) + sin(time * TAU / 8 + n * TAU) * 7)
                    val wing = .65f + abs(sin(time * TAU / 1.5f)) * 2f
                    rotate(sin(time * TAU / 12) * 25, butterfly) {
                        drawOval(c.flower.copy(alpha = .85f), butterfly - Offset(wing * 2, 2.5f), Size(wing * 2, 4f))
                        drawOval(c.light.copy(alpha = .9f), butterfly + Offset(0f, -2.5f), Size(wing * 2, 4f))
                        drawLine(c.shade, butterfly - Offset(0f, 2f), butterfly + Offset(0f, 2f), .5f)
                    }
                } else paintLeaf(p, 1.4f + n, .65f, c.flower.copy(alpha = opacity * .65f), time * 360 / 12 + index * 25)
            }
        }
    }
}

private fun organicCloud(center: Offset, rx: Float, ry: Float, seed: Int): Path {
    val points = (0 until 24).map { i ->
        val a = i * TAU / 24
        val radius = .88f + forestNoise(seed, i, 30) * .16f
        center + Offset(cos(a) * rx * radius, sin(a) * ry * radius)
    }
    return Path().apply {
        val start = (points.last() + points.first()) / 2f
        moveTo(start.x, start.y)
        points.forEachIndexed { i, point ->
            val end = (point + points[(i + 1) % points.size]) / 2f
            quadraticTo(point.x, point.y, end.x, end.y)
        }
        close()
    }
}

private fun roundedPolygon(points: List<Offset>, fraction: Float): Path = Path().apply {
    points.forEachIndexed { index, p ->
        val entry = lerp(p, points[(index + points.size - 1) % points.size], fraction)
        val exit = lerp(p, points[(index + 1) % points.size], fraction)
        if (index == 0) moveTo(entry.x, entry.y) else lineTo(entry.x, entry.y)
        quadraticTo(p.x, p.y, exit.x, exit.y)
    }
    close()
}

private fun lerp(a: Offset, b: Offset, fraction: Float) = a + (b - a) * fraction
