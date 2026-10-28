package com.jas.montaj.montaj.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jas.montaj.common.ColorPaletteRow
import com.jas.montaj.common.Formats
import com.jas.montaj.common.HintText
import com.jas.montaj.common.LabeledSlider
import com.jas.montaj.common.SectionTitle
import com.jas.montaj.montaj.Clip
import com.jas.montaj.montaj.MontajViewModel
import com.jas.montaj.montaj.TextPosition
import com.jas.montaj.montaj.VideoFilter

// region Kliplar

@Composable
fun ClipsPanel(viewModel: MontajViewModel) {
    val clips = viewModel.state.clips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip ->
            ClipCard(
                number = index + 1,
                clip = clip,
                thumbnail = viewModel.thumbnails[clip.id],
                selected = viewModel.selectedClipId == clip.id,
                onClick = { viewModel.selectedClipId = clip.id },
            )
        }
    }
    val index = clips.indexOfFirst { it.id == viewModel.selectedClipId }
    val clip = clips.getOrNull(index)
    if (clip == null) {
        HintText("Tahrirlash uchun klipni tanlang")
        return
    }

    SectionTitle("Qirqish")
    LabeledSlider(
        title = "Boshi",
        value = clip.trimStart.toFloat(),
        range = 0f..clip.durationSec.toFloat(),
        valueText = Formats.seconds(clip.trimStart),
        onChange = { value -> viewModel.updateClip(clip.id) { it.copy(trimStart = minOf(value.toDouble(), it.trimEnd - Clip.MIN_LENGTH)) } },
    )
    LabeledSlider(
        title = "Oxiri",
        value = clip.trimEnd.toFloat(),
        range = 0f..clip.durationSec.toFloat(),
        valueText = Formats.seconds(clip.trimEnd),
        onChange = { value -> viewModel.updateClip(clip.id) { it.copy(trimEnd = maxOf(value.toDouble(), it.trimStart + Clip.MIN_LENGTH)) } },
    )

    SectionTitle("Tezlik")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Clip.SPEEDS.forEach { speed ->
            FilterChip(
                selected = clip.speed == speed,
                onClick = { viewModel.updateClip(clip.id) { it.copy(speed = speed) } },
                label = { Text(Formats.speed(speed)) },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.moveClip(clip.id, -1) }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("Chapga")
        }
        OutlinedButton(onClick = { viewModel.moveClip(clip.id, 1) }, enabled = index < clips.lastIndex) {
            Text("O'ngga")
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { viewModel.deleteClip(clip.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClipCard(number: Int, clip: Clip, thumbnail: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width = 72.dp, height = 110.dp)
            .clip(shape)
            .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .background(Color.DarkGray)
            .clickable(onClick = onClick),
    ) {
        if (thumbnail != null) {
            Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall)
            Text(Formats.seconds(clip.outputDuration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// endregion

// region Ovoz

@Composable
fun AudioPanel(viewModel: MontajViewModel) {
    val state = viewModel.state
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importMusic)
    }

    SectionTitle("Videoning asl ovozi")
    VolumeRow(state.originalVolume, viewModel::setOriginalVolume)

    SectionTitle("Fon musiqasi")
    val music = state.music
    if (music != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(music.name, maxLines = 1)
                HintText(Formats.clock(music.durationSec))
            }
            IconButton(onClick = viewModel::removeMusic) {
                Icon(Icons.Filled.Delete, contentDescription = "Olib tashlash", tint = MaterialTheme.colorScheme.error)
            }
        }
        VolumeRow(state.musicVolume, viewModel::setMusicVolume)
        HintText("Musiqa qisqa bo'lsa takrorlanadi.")
    }
    OutlinedButton(onClick = { pickMusic.launch("audio/*") }) {
        Text(if (music == null) "Musiqa tanlash" else "Boshqa musiqa")
    }
}

@Composable
private fun VolumeRow(volume: Float, onChange: (Float) -> Unit) {
    LabeledSlider(
        title = "Balandlik",
        value = volume,
        range = 0f..1f,
        valueText = Formats.percent(volume.toDouble()),
        onChange = onChange,
        leading = {
            IconButton(onClick = { onChange(if (volume > 0f) 0f else 1f) }) {
                Icon(
                    if (volume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Ovozni o'chirish",
                )
            }
        },
    )
}

// endregion

// region Matn

@Composable
fun TextPanel(viewModel: MontajViewModel) {
    val state = viewModel.state
    Button(onClick = viewModel::addOverlay) { Text("Matn / subtitr qo'shish") }
    if (state.overlays.isEmpty()) {
        HintText("Matn joriy vaqtdan boshlab 3 soniya ko'rinadi. Vaqtini keyin o'zgartirish mumkin.")
    }
    state.overlays.forEach { overlay ->
        val selected = overlay.id == viewModel.selectedOverlayId
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f))
                .clickable { viewModel.selectedOverlayId = if (selected) null else overlay.id }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(overlay.text.ifBlank { "(bo'sh)" }, maxLines = 1)
            HintText("${Formats.seconds(overlay.start)} – ${Formats.seconds(overlay.end)} · ${overlay.position.title}")
            if (selected) {
                val maxTime = state.totalDuration.coerceAtLeast(0.5).toFloat()
                OutlinedTextField(
                    value = overlay.text,
                    onValueChange = { text -> viewModel.updateOverlay(overlay.id) { it.copy(text = text) } },
                    label = { Text("Matn") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSlider(
                    title = "Boshlanishi",
                    value = overlay.start.toFloat(),
                    range = 0f..maxTime,
                    valueText = Formats.seconds(overlay.start),
                    onChange = { v -> viewModel.updateOverlay(overlay.id) { it.copy(start = minOf(v.toDouble(), it.end - 0.2)) } },
                )
                LabeledSlider(
                    title = "Tugashi",
                    value = overlay.end.toFloat(),
                    range = 0f..maxTime,
                    valueText = Formats.seconds(overlay.end),
                    onChange = { v -> viewModel.updateOverlay(overlay.id) { it.copy(end = maxOf(v.toDouble(), it.start + 0.2)) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextPosition.entries.forEach { position ->
                        FilterChip(
                            selected = overlay.position == position,
                            onClick = { viewModel.updateOverlay(overlay.id) { it.copy(position = position) } },
                            label = { Text(position.title) },
                        )
                    }
                }
                LabeledSlider(
                    title = "O'lcham",
                    value = overlay.fontSize,
                    range = 36f..140f,
                    valueText = "${overlay.fontSize.toInt()}",
                    onChange = { v -> viewModel.updateOverlay(overlay.id) { it.copy(fontSize = v) } },
                )
                ColorPaletteRow("Rang", overlay.color) { color -> viewModel.updateOverlay(overlay.id) { it.copy(color = color) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Orqa fon (subtitr uslubi)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = overlay.hasBackground,
                        onCheckedChange = { on -> viewModel.updateOverlay(overlay.id) { it.copy(hasBackground = on) } },
                    )
                }
                TextButton(onClick = { viewModel.deleteOverlay(overlay.id) }) {
                    Text("Matnni o'chirish", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// endregion

// region Filtr

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(viewModel: MontajViewModel) {
    val source = viewModel.state.clips.firstOrNull()?.let { viewModel.thumbnails[it.id] }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VideoFilter.entries.forEach { filter ->
            val selected = viewModel.state.filter == filter
            val preview = remember(source, filter) { source?.let { applyPreview(it, filter) } }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { viewModel.setFilter(filter) },
            ) {
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .size(width = 76.dp, height = 100.dp)
                        .clip(shape)
                        .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .background(Color.DarkGray),
                ) {
                    if (preview != null) {
                        Image(
                            preview.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 76.dp, height = 100.dp),
                        )
                    }
                }
                Text(
                    filter.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun applyPreview(source: Bitmap, filter: VideoFilter): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(filter.previewMatrix()) }
    AndroidCanvas(result).drawBitmap(source, 0f, 0f, paint)
    return result
}

// endregion
