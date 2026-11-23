package com.bililisten.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

@Composable
internal fun VinylRecord(coverUrl: String?, isPlaying: Boolean, modifier: Modifier = Modifier,
    rotation: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) }) {
    val colors = MaterialTheme.colorScheme
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val armAngle by animateFloatAsState(if (isPlaying) 0f else -32f, tween(450), label = "tonearm")
    LaunchedEffect(isPlaying, lifecycle) {
        if (isPlaying) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                rotation.animateTo(rotation.value + 360f, tween(24_000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    // Let aspectRatio fit both available dimensions; forcing the width would overflow short screens.
    BoxWithConstraints(modifier.aspectRatio(1.08f)
        .semantics { contentDescription = if (isPlaying) "黑胶唱片旋转，唱针已落下" else "黑胶唱片暂停，唱针已抬起" }) {
        val discSize = maxWidth * .84f
        val unit = maxWidth / 340f
        VinylDisc(coverUrl, Modifier.align(Alignment.BottomCenter).size(discSize)
            .graphicsLayer { rotationZ = rotation.value })
        Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * .76f, size.height * .065f)
            drawCircle(Color(0xFF0A1117), (unit * 17).toPx(), pivot + Offset((unit * 2).toPx(), (unit * 3).toPx()))
            rotate(armAngle, pivot) {
                val elbow = Offset(size.width * .82f, size.height * .29f)
                val tip = Offset(size.width * .78f, size.height * .48f)
                val path = Path().apply { moveTo(pivot.x, pivot.y); lineTo(elbow.x, elbow.y); lineTo(tip.x, tip.y) }
                drawPath(path, Color(0xFF06090D), style = Stroke((unit * 10).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Brush.linearGradient(listOf(Color(0xFFE1E8E9), Color(0xFF74888D))),
                    style = Stroke((unit * 6).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(colors.primary, tip - Offset((-unit * 3).toPx(), (unit * 4).toPx()),
                    tip + Offset((-unit * 4).toPx(), (unit * 7).toPx()), (unit * 11).toPx(), StrokeCap.Round)
            }
            drawCircle(Color(0xFF38494E), (unit * 14).toPx(), pivot)
            drawCircle(Color(0xFF9BAFB2), (unit * 8).toPx(), pivot)
            drawCircle(Color(0xFF24353C), (unit * 3).toPx(), pivot)
        }
    }
}

/** The same record artwork is shared by the turntable and the collection browser. */
@Composable
internal fun VinylDisc(coverUrl: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.clip(CircleShape)
        .background(Brush.radialGradient(listOf(Color(0xFF272D32), Color(0xFF090D10))))) {
        val unit = maxWidth / 285.6f
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            for (ring in 1..25) drawCircle(Color(0xFF687077).copy(alpha = .16f),
                radius * (.57f + ring * .016f), style = Stroke(unit.toPx() * .5f))
            drawCircle(Color(0xFF59616A), radius - unit.toPx(), style = Stroke(unit.toPx()))
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color(0x22FFFFFF),
                Color.Transparent, Color(0x14FFFFFF), Color.Transparent)), radius)
        }
        Box(Modifier.align(Alignment.Center).fillMaxSize(.58f).clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.secondaryContainer, colors.background))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Headphones, null, Modifier.size(unit * 46), tint = colors.primary)
            if (coverUrl != null) CachedArtwork(coverUrl, null, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF090D10), (unit * 6).toPx())
            drawCircle(Color(0xFFB0BEBF), (unit * 2).toPx())
        }
    }
}
