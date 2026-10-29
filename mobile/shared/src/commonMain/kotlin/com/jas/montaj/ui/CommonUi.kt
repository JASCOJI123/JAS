package com.jas.montaj.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.Colors
import com.jas.montaj.core.ExportState

val Accent = Color(0xFF20D5EC)

/** CapCut uslubidagi qorong'i mavzu. */
@Composable
fun MontajTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            secondary = Color(0xFFFE2C55),
            background = Color.Black,
            surface = Color(0xFF141414),
            surfaceVariant = Color(0xFF242426),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF242426),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFF8E8E93),
        ),
        content = content,
    )
}

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

/** Telefon uchun qulay rang tanlash qatori (ARGB). */
@Composable
fun ColorPaletteRow(title: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            items(Colors.palette) { color ->
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
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ExportDialog(state: ExportState, onShare: () -> Unit, onDismiss: () -> Unit) {
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
            confirmButton = { Button(onClick = onShare) { Text("Ulashish") } },
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
