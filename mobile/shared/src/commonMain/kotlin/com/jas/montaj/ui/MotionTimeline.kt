package com.jas.montaj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jas.montaj.motion.LayerContent
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.motion.MotionLayer

private fun LayerContent.icon(): ImageVector = when (this) {
    is LayerContent.Video -> Icons.Filled.Movie
    is LayerContent.Image -> Icons.Filled.Image
    is LayerContent.Text -> Icons.Filled.TextFields
    is LayerContent.Shape -> Icons.Filled.Category
}

private fun LayerContent.color(): Color = when (this) {
    is LayerContent.Video -> Color(0xFF3B6CFF)
    is LayerContent.Image -> Color(0xFF1DB37A)
    is LayerContent.Text -> CapCut.TextTrack
    is LayerContent.Shape -> Color(0xFFFF9F0A)
}

/**
 * Qatlamlar timeline'i (eng yuqoridagi qatlam birinchi): har biri In..Out bo'lagi,
 * sariq rombchalar — keyframe'lar. Tanlangan qatlam chetlarini tortib In/Out o'zgaradi,
 * uzoq bosib surilsa qatlam vaqt bo'yicha ko'chadi.
 */
@Composable
fun MotionLayerTimeline(editor: MotionEditor, pixelsPerSecond: Dp, onZoom: (Float) -> Unit) {
    val project = editor.project
    TimelineViewport(
        currentTime = editor.currentTime,
        duration = project.duration,
        pixelsPerSecond = pixelsPerSecond,
        onSeek = editor::seek,
        onZoom = onZoom,
        modifier = Modifier.height(200.dp),
    ) { scope ->
        // Loyiha chegarasi.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.offset(x = scope.x(0.0)).size(width = scope.width(project.duration), height = 2.dp).background(CapCut.Surface))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (project.layers.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.offset(x = scope.x(0.0))) {
                    TrackHint("+ Matn qo'shish") { editor.addText() }
                }
            }
            project.layers.asReversed().forEach { layer ->
                LayerTrack(scope, editor, layer, editor.selectedLayerId == layer.id)
            }
        }
    }
}

@Composable
private fun LayerTrack(scope: TimelineScope, editor: MotionEditor, layer: MotionLayer, selected: Boolean) {
    val secondsPerPx = 1.0 / with(LocalDensity.current) { scope.pixelsPerSecond.toPx() }
    val duration = editor.project.duration
    val end = minOf(layer.outPoint, duration)
    val move by rememberUpdatedState { dx: Float -> editor.shiftLayer(layer.id, dx * secondsPerPx) }
    Box(Modifier.fillMaxWidth().height(32.dp)) {
        TrackBar(
            label = layer.name,
            color = layer.content.color().copy(alpha = if (layer.visible) 1f else 0.35f),
            icon = layer.content.icon(),
            selected = selected,
            modifier = Modifier
                .offset(x = scope.x(layer.inPoint))
                .pointerInput(layer.id) {
                    detectDragGesturesAfterLongPress { change, drag ->
                        change.consume()
                        move(drag.x)
                    }
                },
            width = scope.width(end - layer.inPoint),
            height = 26.dp,
            onClick = { editor.selectedLayerId = if (selected) null else layer.id },
        )
        layer.allKeyframeTimes.distinct().forEach { time ->
            Box(
                Modifier
                    .offset(x = scope.x(time) - 5.dp, y = 11.dp)
                    .size(10.dp)
                    .rotate(45f)
                    .background(Color(0xFFFFD91A)),
            )
        }
        if (selected) {
            TrimHandle(Modifier.offset(x = scope.x(layer.inPoint) - 14.dp), height = 26.dp) { dx ->
                editor.updateLayer(layer.id) { it.copy(inPoint = (it.inPoint + dx * secondsPerPx).coerceIn(0.0, it.outPoint - 0.1)) }
            }
            TrimHandle(Modifier.offset(x = scope.x(end)), height = 26.dp) { dx ->
                editor.updateLayer(layer.id) { it.copy(outPoint = (it.outPoint + dx * secondsPerPx).coerceIn(it.inPoint + 0.1, duration)) }
            }
        }
    }
}
