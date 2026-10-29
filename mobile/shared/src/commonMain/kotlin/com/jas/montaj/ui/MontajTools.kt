@file:OptIn(ExperimentalLayoutApi::class)

package com.jas.montaj.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.Format
import com.jas.montaj.core.Transcriber
import com.jas.montaj.montaj.Clip
import com.jas.montaj.montaj.MontajEditor
import com.jas.montaj.montaj.TextOverlayItem
import com.jas.montaj.montaj.TextPosition
import com.jas.montaj.montaj.TransitionKind
import com.jas.montaj.montaj.VideoFilter
import com.jas.montaj.platform.PickKind
import com.jas.montaj.platform.rememberMediaPicker
import com.jas.montaj.platform.rememberMicrophonePermission
import com.jas.montaj.render.Graphics

// region Klip asboblari

@Composable
internal fun SpeedPanel(editor: MontajEditor, clip: Clip) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Clip.SPEEDS.forEach { speed ->
            FilterChip(
                selected = clip.speed == speed,
                onClick = { editor.updateClip(clip.id) { it.copy(speed = speed) } },
                label = { Text(Format.speed(speed)) },
            )
        }
    }
    HintText("Klip uzunligi: ${Format.seconds(clip.outputDuration)} (asl ${Format.seconds(clip.trimmedDuration)})")
    SectionTitle("Qirqish")
    LabeledSlider("Boshi", clip.trimStart.toFloat(), 0f..clip.durationSec.toFloat(), Format.seconds(clip.trimStart), { value ->
        editor.updateClip(clip.id) { it.copy(trimStart = minOf(value.toDouble(), it.trimEnd - Clip.MIN_LENGTH)) }
    })
    LabeledSlider("Oxiri", clip.trimEnd.toFloat(), 0f..clip.durationSec.toFloat(), Format.seconds(clip.trimEnd), { value ->
        editor.updateClip(clip.id) { it.copy(trimEnd = maxOf(value.toDouble(), it.trimStart + Clip.MIN_LENGTH)) }
    })
    HintText("Timeline'da klip chetidagi oq tutqichlarni tortib ham qirqish mumkin.")
}

@Composable
internal fun TransitionPanel(editor: MontajEditor, clip: Clip) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TransitionKind.entries.forEach { kind ->
            FilterChip(
                selected = clip.transition == kind,
                onClick = { editor.updateClip(clip.id) { it.copy(transition = kind) } },
                label = { Text(kind.title) },
            )
        }
    }
    if (clip.transition != TransitionKind.NONE) {
        LabeledSlider("Davomiyligi", clip.transitionDuration.toFloat(), 0.2f..1.5f, Format.seconds(clip.transitionDuration), { v ->
            editor.updateClip(clip.id) { it.copy(transitionDuration = v.toDouble()) }
        })
        HintText("O'tishni ko'rish uchun pauzada kliplar chegarasiga suring. Tayyor videoda to'liq chiqadi.")
    }
}

// endregion

// region Ovoz

@Composable
internal fun AudioPanel(editor: MontajEditor) {
    val state = editor.state
    val pickMusic = rememberMediaPicker(PickKind.AUDIO, multiple = false, onPicked = editor::importMusic)

    SectionTitle("Videoning asl ovozi")
    VolumeRow(state.originalVolume, editor::setOriginalVolume)

    SectionTitle("Fon musiqasi")
    val music = state.music
    if (music != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = CapCut.AudioTrack)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(music.name, maxLines = 1)
                HintText(Format.clock(music.durationSec))
            }
            IconButton(onClick = editor::removeMusic) {
                Icon(Icons.Filled.Delete, contentDescription = "Olib tashlash", tint = MaterialTheme.colorScheme.error)
            }
        }
        VolumeRow(state.musicVolume, editor::setMusicVolume)
        HintText("Musiqa qisqa bo'lsa takrorlanadi.")
    }
    OutlinedButton(onClick = pickMusic) { Text(if (music == null) "Musiqa tanlash" else "Boshqa musiqa") }

    VoiceoverSection(editor)
}

@Composable
internal fun VoiceoverSection(editor: MontajEditor) {
    val requestMicrophone = rememberMicrophonePermission { granted ->
        if (granted) {
            editor.startRecording()
        } else {
            editor.errorMessage = "Ovoz yozish uchun mikrofonga ruxsat bering"
        }
    }
    SectionTitle("Ovoz yozish (voiceover)")
    if (editor.isRecording) {
        Button(
            onClick = editor::stopRecording,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Text("To'xtatish")
        }
        HintText("Yozilmoqda… Video ijro etilyapti — ustiga gapiring.")
    } else {
        OutlinedButton(onClick = requestMicrophone) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Text("Joriy joydan yozish")
        }
        HintText("Yozuv videoning joriy soniyasidan boshlanadi. Quloqchin bilan yozsangiz, video ovozi aralashmaydi.")
    }
    editor.state.voiceovers.forEachIndexed { index, voice ->
        Column(Modifier.fillMaxWidth().background(CapCut.Surface, RoundedCornerShape(10.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = CapCut.VoiceTrack)
                Text(
                    "Yozuv ${index + 1}: ${Format.seconds(voice.startAt)} dan, ${Format.seconds(voice.duration)}",
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = { editor.deleteVoiceover(voice.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
                }
            }
            VolumeRow(voice.volume) { volume -> editor.updateVoiceover(voice.id) { it.copy(volume = volume) } }
        }
    }
}

@Composable
internal fun VolumeRow(volume: Float, onChange: (Float) -> Unit) {
    LabeledSlider(
        title = "Balandlik",
        value = volume,
        range = 0f..1f,
        valueText = Format.percent(volume.toDouble()),
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

/** "Matn" asbobi: yangi matn, avto-subtitr va mavjud matnlar ro'yxati. */
@Composable
internal fun TextToolPanel(editor: MontajEditor, onEdit: (String) -> Unit) {
    var showAuto by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            editor.addOverlay()
            editor.selectedOverlayId?.let(onEdit)
        }) { Text("Matn qo'shish") }
        OutlinedButton(onClick = { showAuto = true }) { Text("Avto-subtitr") }
    }
    if (showAuto) AutoSubtitleDialog(editor) { showAuto = false }
    if (editor.state.overlays.isEmpty()) {
        HintText("Matn joriy vaqtdan boshlab 3 soniya ko'rinadi. Timeline'dagi binafsha bo'lakni bosib tahrirlaysiz.")
    }
    editor.state.overlays.forEach { overlay ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CapCut.Surface)
                .clickable { onEdit(overlay.id) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(overlay.text.ifBlank { "(bo'sh)" }, maxLines = 1, modifier = Modifier.weight(1f))
            HintText("${Format.seconds(overlay.start)} – ${Format.seconds(overlay.end)}")
        }
    }
}

@Composable
internal fun TextEditPanel(editor: MontajEditor, overlay: TextOverlayItem) {
    val maxTime = editor.state.totalDuration.coerceAtLeast(0.5).toFloat()
    OutlinedTextField(
        value = overlay.text,
        onValueChange = { text -> editor.updateOverlay(overlay.id) { it.copy(text = text) } },
        label = { Text("Matn") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextPosition.entries.forEach { position ->
            FilterChip(
                selected = overlay.position == position,
                onClick = { editor.updateOverlay(overlay.id) { it.copy(position = position) } },
                label = { Text(position.title) },
            )
        }
    }
    LabeledSlider("O'lcham", overlay.fontSize, 36f..140f, "${overlay.fontSize.toInt()}", { v ->
        editor.updateOverlay(overlay.id) { it.copy(fontSize = v) }
    })
    ColorPaletteRow("Rang", overlay.color) { color -> editor.updateOverlay(overlay.id) { it.copy(color = color) } }
    SwitchRow("Orqa fon (subtitr uslubi)", overlay.hasBackground) { on -> editor.updateOverlay(overlay.id) { it.copy(hasBackground = on) } }
    LabeledSlider("Boshlanishi", overlay.start.toFloat(), 0f..maxTime, Format.seconds(overlay.start), { v ->
        editor.updateOverlay(overlay.id) { it.copy(start = minOf(v.toDouble(), it.end - 0.2)) }
    })
    LabeledSlider("Tugashi", overlay.end.toFloat(), 0f..maxTime, Format.seconds(overlay.end), { v ->
        editor.updateOverlay(overlay.id) { it.copy(end = maxOf(v.toDouble(), it.start + 0.2)) }
    })
}

@Composable
private fun AutoSubtitleDialog(editor: MontajEditor, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(editor.settings.openAiKey) }
    var language by remember { mutableStateOf(editor.settings.subtitleLanguage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avto-subtitr") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HintText("Kliplardagi nutq OpenAI Whisper orqali matnga aylantiriladi va vaqtlari bilan subtitr qo'shiladi.")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("OpenAI API kaliti") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                HintText("Kalit faqat shu telefonda saqlanadi. platform.openai.com → API keys.")
                Text("Til", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Transcriber.languages.forEach { (code, title) ->
                        FilterChip(selected = language == code, onClick = { language = code }, label = { Text(title) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                editor.saveSettings(editor.settings.copy(openAiKey = key.trim(), subtitleLanguage = language))
                onDismiss()
                editor.generateSubtitles()
            }) { Text("Boshlash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}

// endregion

// region Filtr

@Composable
internal fun FilterPanel(editor: MontajEditor) {
    val source = editor.state.clips.firstOrNull()?.let { editor.thumbnails[it.id] }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VideoFilter.entries.forEach { filter ->
            val selected = editor.state.filter == filter
            val preview = remember(source, filter) {
                source?.let { image -> filter.matrix()?.let { Graphics.withColorMatrix(image, it) } ?: image }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { editor.setFilter(filter) }) {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .size(width = 64.dp, height = 64.dp)
                        .clip(shape)
                        .border(2.dp, if (selected) CapCut.Accent else Color.Transparent, shape)
                        .background(CapCut.Surface),
                ) {
                    if (preview != null) {
                        Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
                Text(
                    filter.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) CapCut.Accent else Color.White,
                )
            }
        }
    }
}

// endregion
