package com.jas.montaj.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Sarlavha, joriy qiymat va slider. */
@Composable
fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(title, style = MaterialTheme.typography.bodyMedium)
            trailing?.invoke()
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

private val paletteColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF5E3A, 0xFFFFD91A, 0xFFFF4040, 0xFF33D9FF,
    0xFF3A7BFF, 0xFF8E44FF, 0xFFFF4FB8, 0xFF2ECC71, 0xFF7F8C8D, 0xFF1E1E2E,
).map { it.toInt() }

/** Telefon uchun qulay rang tanlash qatori (ARGB). */
@Composable
fun ColorPaletteRow(title: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            items(paletteColors) { color ->
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(color), CircleShape)
                        .border(
                            width = if (color == selected) 3.dp else 1.dp,
                            color = if (color == selected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(color) },
                )
            }
        }
    }
}

@Composable
fun ExportDialog(state: ExportState, context: Context, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Idle -> Unit
        is ExportState.Exporting -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Saqlanmoqda… ${(state.progress * 100).toInt()}%") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text("Ilovadan chiqmang", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        is ExportState.Finished -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2ECC71)) },
            title = { Text("Video galereyaga saqlandi") },
            text = { Text("Movies/Montaj papkasida") },
            confirmButton = {
                Button(onClick = { shareVideo(context, state.uri) }) { Text("Ulashish") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Yopish") } },
        )
    }
}

@Composable
fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text)
        }
    }
}

@Composable
fun ErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xatolik") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun HintText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun HorizontalGap(width: Int = 8) = Spacer(Modifier.width(width.dp))

fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Ulashish").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

object Formats {
    /** 75.4 -> "1:15" */
    fun clock(seconds: Double): String {
        val total = Math.round(seconds).toInt().coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    /** 3.25 -> "0:03.2" */
    fun preciseClock(seconds: Double): String {
        val safe = seconds.coerceAtLeast(0.0)
        val minutes = (safe / 60).toInt()
        return String.format(Locale.US, "%d:%04.1f", minutes, safe - minutes * 60)
    }

    fun seconds(value: Double): String = String.format(Locale.US, "%.1f s", value)
    fun speed(value: Float): String = if (value % 1f == 0f) "${value.toInt()}x" else "${value}x"
    fun percent(value: Double): String = "${Math.round(value * 100)}%"
    fun degrees(value: Double): String = "${Math.round(value)}°"
    fun pixels(value: Double): String = "${Math.round(value)} px"
    fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value)
}

fun Color.argb(): Int = toArgb()
