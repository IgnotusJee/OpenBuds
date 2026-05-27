package dev.ignotus.openbuds.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
internal fun BoxScope.InteractiveHighlight(
    enabled: Boolean,
    highlightColor: Color = Color.White,
) {
    if (!enabled) return

    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    val highlightAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(false)
                    touchPosition = down.position
                    scope.launch { highlightAlpha.animateTo(1f, spring(0.5f, 300f)) }
                    drag(down.id) { change ->
                        touchPosition = change.position
                    }
                    scope.launch { highlightAlpha.animateTo(0f, spring(0.5f, 300f)) }
                }
            },
    ) {
        if (size != IntSize.Zero && highlightAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = min(size.width, size.height).toFloat() * 1.2f
                val alpha = highlightAlpha.value
                clipRect {
                    drawCircle(
                        color = highlightColor.copy(alpha = 0.06f * alpha),
                        radius = radius,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                highlightColor.copy(alpha = 0.18f * alpha),
                                highlightColor.copy(alpha = 0f),
                            ),
                            center = touchPosition,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                }
            }
        }
    }
}
