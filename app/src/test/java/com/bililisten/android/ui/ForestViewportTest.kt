package com.bililisten.android.ui

import androidx.compose.ui.geometry.Size
import com.bililisten.android.data.ForestGrove
import com.bililisten.android.data.ForestGrowth
import com.bililisten.android.data.TREE_LISTENING_MS
import org.junit.Assert.*
import org.junit.Test

class ForestViewportTest {
    @Test fun cameraFitsTheWholeForestAtEverySizeAndKeepsZoomingOut() {
        val sizes = listOf(Size(320f, 120f), Size(360f, 440f), Size(720f, 800f))
        for (size in sizes) {
            var previousZoom = Float.MAX_VALUE
            for (total in listOf(4L, 40L, 1_000L, 100_000L).map { it * 16 * TREE_LISTENING_MS } + Long.MAX_VALUE) {
                val grove = ForestGrove.create(ForestGrowth(total))
                val camera = forestViewport(grove, size)
                assertTrue(camera.zoom > 0f && camera.zoom < previousZoom)
                assertTrue(camera.origin.x + camera.bounds.left * camera.zoom >= -.001f)
                assertTrue(camera.origin.y + camera.bounds.top * camera.zoom >= -.001f)
                assertTrue(camera.origin.x + camera.bounds.right * camera.zoom <= size.width + .001f)
                assertTrue(camera.origin.y + camera.bounds.bottom * camera.zoom <= size.height + .001f)
                previousZoom = camera.zoom
            }
        }
    }
}
