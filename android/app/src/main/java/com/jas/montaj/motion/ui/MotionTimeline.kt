package com.jas.montaj.motion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jas.montaj.motion.MotionViewModel
import com.jas.montaj.motion.model.LayerContent
import com.jas.montaj.motion.model.MotionLayer

/** After Effects'dagi kabi qatlamlar ro'yxati: eng yuqoridagi qatlam birinchi. */
@Composable
fun LayerTimeline(viewModel: MotionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        viewModel.project.layers.asReversed().forEach { layer ->
            LayerRow(
                layer = layer,
                duration = viewModel.project.duration,
                currentTime = viewModel.currentTime,
                selected = viewModel.selectedLayerId == layer.id,
                onClick = { viewModel.selectedLayerId = if (viewModel.selectedLayerId == layer.id) null else layer.id },
                onToggleVisibility = { viewModel.updateLayer(layer.id) { it.copy(visible = !it.visible) } },
            )
        }
    }
}

private fun LayerContent.icon(): ImageVector = when (this) {
    is LayerContent.Video -> Icons.Filled.Movie
    is LayerContent.Image -> Icons.Filled.Image
    is LayerContent.Text -> Icons.Filled.TextFields
    is LayerContent.Shape -> Icons.Filled.Category
}

@Composable
private fun LayerRow(
    layer: MotionLayer,
    duration: Double,
    currentTime: Double,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleVisibility: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleVisibility) {
            Icon(
                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = "Ko'rinish",
                tint = if (layer.visible) MaterialTheme.colorScheme.onSurface else Color.Gray,
            )
        }
        Icon(layer.content.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(
            layer.name,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp).padding(start = 8.dp),
        )
        val keyframes = layer.allKeyframeTimes.distinct()
        Canvas(Modifier.weight(1f).height(18.dp)) {
            val safeDuration = duration.coerceAtLeast(0.1)
            fun x(time: Double) = (time / safeDuration * size.width).toFloat()
            drawRoundRect(Color.Gray.copy(alpha = 0.15f), cornerRadius = CornerRadius(size.height / 2))
            val start = x(layer.inPoint)
            val end = x(minOf(layer.outPoint, duration)).coerceAtLeast(start + 4f)
            drawRoundRect(
                accent.copy(alpha = if (layer.visible) 0.45f else 0.15f),
                topLeft = Offset(start, 0f),
                size = Size(end - start, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
            keyframes.forEach { time ->
                val cx = x(time)
                val cy = size.height / 2
                val r = size.height * 0.3f
                drawPath(
                    Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    },
                    Color(0xFFFFD91A),
                )
            }
            val playhead = x(currentTime)
            drawLine(Color.Red, Offset(playhead, 0f), Offset(playhead, size.height), strokeWidth = 3f)
        }
    }
}
