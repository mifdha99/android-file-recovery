package com.eslam.recovery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.eslam.recovery.domain.model.RecoverableFile

/**
 * Pinch-zoom / pan / double-tap image. Pointer events are only consumed while zoomed or with two
 * fingers down, so a one-finger swipe at 1× still reaches the surrounding pager.
 */
@Composable
fun ZoomableImage(item: RecoverableFile, onZoomChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    // Full-resolution decode of a 100 MP photo would exhaust memory; 2048 px is plenty for a phone screen.
    val request = remember(item.id) { ImageRequest.Builder(context).data(item).size(Size(2048, 2048)).build() }

    fun update(newScale: Float, pan: Offset, width: Int, height: Int) {
        scale = newScale.coerceIn(1f, 6f)
        val maxX = width * (scale - 1) / 2
        val maxY = height * (scale - 1) / 2
        offset = if (scale == 1f) Offset.Zero
        else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
        onZoomChanged(scale > 1f)
    }

    AsyncImage(
        model = request,
        contentDescription = item.displayName,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(onDoubleTap = { update(if (scale > 1f) 1f else 2.5f, Offset.Zero, size.width, size.height) })
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1f) {
                            update(scale * event.calculateZoom(), event.calculatePan(), size.width, size.height)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
