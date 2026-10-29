@file:OptIn(ExperimentalLayoutApi::class)

package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.Format
import com.jas.montaj.motion.Bezier

/** Qiymat o'qi −0.5..1.5 oralig'ida ko'rsatiladi (overshoot uchun joy). */
private const val VALUE_MIN = -0.5f
private const val VALUE_SPAN = 2f

/**
 * Graph editor: keyframe'dan keyingisigacha bo'lgan tezlik egri chizig'i.
 * Ikki tutqichni surib, harakat qanday tezlashishi/sekinlashishini o'zgartirasiz.
 */
@Composable
fun GraphEditorDialog(initial: Bezier, onApply: (Bezier?) -> Unit, onDismiss: () -> Unit) {
    var curve by remember { mutableStateOf(initial) }
    var dragging by remember { mutableStateOf(0) }
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Graph editor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            fun toCurve(offset: Offset): Pair<Double, Double> {
                                val x = (offset.x / size.width).coerceIn(0f, 1f)
                                val y = (VALUE_MIN + (1 - offset.y / size.height) * VALUE_SPAN).coerceIn(VALUE_MIN, VALUE_MIN + VALUE_SPAN)
                                return x.toDouble() to y.toDouble()
                            }
                            fun handle(x: Double, y: Double) = Offset(
                                (x * size.width).toFloat(),
                                ((1 - (y - VALUE_MIN) / VALUE_SPAN) * size.height).toFloat(),
                            )
                            detectDragGestures(
                                onDragStart = { start ->
                                    val first = handle(curve.x1, curve.y1)
                                    val second = handle(curve.x2, curve.y2)
                                    dragging = if ((start - first).getDistance() <= (start - second).getDistance()) 1 else 2
                                },
                                onDragEnd = { dragging = 0 },
                                onDrag = { change, _ ->
                                    val (x, y) = toCurve(change.position)
                                    curve = if (dragging == 1) curve.copy(x1 = x, y1 = y) else curve.copy(x2 = x, y2 = y)
                                },
                            )
                        },
                ) {
                    fun point(x: Double, y: Double) = Offset(
                        (x * size.width).toFloat(),
                        ((1 - (y - VALUE_MIN) / VALUE_SPAN) * size.height).toFloat(),
                    )
                    val grid = Color.White.copy(alpha = 0.12f)
                    drawLine(grid, point(0.0, 0.0), point(1.0, 0.0), strokeWidth = 2f)
                    drawLine(grid, point(0.0, 1.0), point(1.0, 1.0), strokeWidth = 2f)
                    drawLine(grid, point(0.0, 0.0), point(1.0, 1.0), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))

                    val path = Path()
                    for (step in 0..60) {
                        val t = step / 60.0
                        val p = point(t, curve.apply(t))
                        if (step == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, accent, style = Stroke(width = 5f))

                    val start = point(0.0, 0.0)
                    val end = point(1.0, 1.0)
                    val first = point(curve.x1, curve.y1)
                    val second = point(curve.x2, curve.y2)
                    drawLine(Color.White.copy(alpha = 0.6f), start, first, strokeWidth = 2f)
                    drawLine(Color.White.copy(alpha = 0.6f), end, second, strokeWidth = 2f)
                    drawCircle(Color(0xFFFFD91A), radius = 14f, center = first)
                    drawCircle(Color(0xFFFFD91A), radius = 14f, center = second)
                    drawCircle(Color.White, radius = 7f, center = start)
                    drawCircle(Color.White, radius = 7f, center = end)
                }
                HintText(
                    "cubic-bezier(${Format.twoDecimals(curve.x1)}, ${Format.twoDecimals(curve.y1)}, " +
                        "${Format.twoDecimals(curve.x2)}, ${Format.twoDecimals(curve.y2)})",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bezier.presets.forEach { (title, preset) ->
                        AssistChip(onClick = { curve = preset }, label = { Text(title) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(curve)
                onDismiss()
            }) { Text("Qo'llash") }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(null)
                onDismiss()
            }) { Text("Grafikni olib tashlash") }
        },
    )
}
