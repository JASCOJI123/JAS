@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.Transcriber
import com.jas.montaj.core.ExportState
import com.jas.montaj.core.Format
import com.jas.montaj.montaj.Clip
import com.jas.montaj.montaj.MontajEditor
import com.jas.montaj.montaj.MontajState
import com.jas.montaj.montaj.TextPosition
import com.jas.montaj.montaj.TransitionKind
import com.jas.montaj.montaj.VideoFilter
import com.jas.montaj.platform.PickKind
import com.jas.montaj.platform.PlayerSurface
import com.jas.montaj.platform.rememberMediaPicker
import com.jas.montaj.platform.rememberMicrophonePermission
import com.jas.montaj.render.Graphics

private val panelTitles = listOf("Kliplar", "Ovoz", "Matn", "Filtr")

@Composable
fun MontajScreen(editor: MontajEditor, modifier: Modifier = Modifier) {
    var panel by rememberSaveable { mutableIntStateOf(0) }
    var showProjects by remember { mutableStateOf(false) }
    val pickVideos = rememberMediaPicker(PickKind.VIDEO, multiple = true, onPicked = editor::importVideos)

    Box(modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(editor.projectInfo.name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = pickVideos) { Icon(Icons.Filled.Add, contentDescription = "Video qo'shish") }
                    },
                    actions = {
                        UndoRedoButtons(editor.history, editor::undo, editor::redo)
                        ProjectsButton {
                            editor.refreshProjects()
                            showProjects = true
                        }
                        TextButton(
                            onClick = editor::export,
                            enabled = editor.state.clips.isNotEmpty() && editor.exportState == ExportState.Idle,
                        ) { Text("Saqlash") }
                    },
                )
            },
        ) { padding ->
            if (editor.state.clips.isEmpty()) {
                EmptyState(Modifier.padding(padding), pickVideos)
            } else {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Preview(editor)
                    Transport(editor)
                    TabRow(selectedTabIndex = panel) {
                        panelTitles.forEachIndexed { index, title ->
                            Tab(selected = panel == index, onClick = { panel = index }, text = { Text(title) })
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (panel) {
                            0 -> ClipsPanel(editor)
                            1 -> AudioPanel(editor)
                            2 -> TextPanel(editor)
                            else -> FilterPanel(editor)
                        }
                    }
                }
            }
        }
        if (editor.isImporting) BusyOverlay("Yuklanmoqda…")
        editor.subtitleProgress?.let { BusyOverlay("Subtitr yaratilmoqda… ${(it * 100).toInt()}%") }
        ExportDialog(editor.exportState, onShare = editor::share, onDismiss = editor::dismissExport)
        ErrorDialog(editor.errorMessage) { editor.errorMessage = null }
        if (showProjects) {
            ProjectsDialog(
                current = editor.projectInfo,
                projects = editor.projects,
                onOpen = { editor.openProject(it); showProjects = false },
                onNew = { editor.newProject(); showProjects = false },
                onRename = editor::renameProject,
                onDelete = editor::deleteProject,
                onDismiss = { showProjects = false },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onPick: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Montajni boshlash uchun video tanlang", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPick) { Text("Video qo'shish") }
    }
}

@Composable
private fun Preview(editor: MontajEditor) {
    Box(Modifier.fillMaxWidth().height(330.dp).background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(MontajState.WIDTH / MontajState.HEIGHT.toFloat())) {
            PlayerSurface(editor.player, Modifier.fillMaxSize())
            val transitionFrame = editor.transitionPreview
            if (transitionFrame != null) {
                // Pauzada o'tish effekti ustida: aniq kadr (matnlar ham ichida).
                Image(transitionFrame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                return@Box
            }
            // Matnlar eksportdagi bilan bir xil funksiya orqali, kichraytirib chiziladi.
            val time = editor.currentTime
            val active = editor.state.overlays.filter { time >= it.start && time < it.end }
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / MontajState.WIDTH
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.scale(scale, scale)
                    active.forEach { editor.previewText.drawOverlay(canvas, it, MontajState.WIDTH.toFloat(), MontajState.HEIGHT.toFloat()) }
                    canvas.restore()
                }
            }
        }
    }
}

@Composable
private fun Transport(editor: MontajEditor) {
    val total = editor.state.totalDuration
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = editor::togglePlayback) {
            Icon(if (editor.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Ijro")
        }
        Slider(
            value = editor.currentTime.toFloat().coerceIn(0f, total.toFloat().coerceAtLeast(0.01f)),
            onValueChange = { editor.seek(it.toDouble()) },
            valueRange = 0f..total.toFloat().coerceAtLeast(0.01f),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${Format.clock(editor.currentTime)} / ${Format.clock(total)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// region Kliplar

@Composable
private fun ClipsPanel(editor: MontajEditor) {
    val clips = editor.state.clips
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip ->
            ClipCard(index + 1, clip, editor.thumbnails[clip.id], editor.selectedClipId == clip.id) { editor.selectedClipId = clip.id }
        }
    }
    val index = clips.indexOfFirst { it.id == editor.selectedClipId }
    val clip = clips.getOrNull(index)
    if (clip == null) {
        HintText("Tahrirlash uchun klipni tanlang")
        return
    }

    SectionTitle("Qirqish")
    LabeledSlider("Boshi", clip.trimStart.toFloat(), 0f..clip.durationSec.toFloat(), Format.seconds(clip.trimStart), { value ->
        editor.updateClip(clip.id) { it.copy(trimStart = minOf(value.toDouble(), it.trimEnd - Clip.MIN_LENGTH)) }
    })
    LabeledSlider("Oxiri", clip.trimEnd.toFloat(), 0f..clip.durationSec.toFloat(), Format.seconds(clip.trimEnd), { value ->
        editor.updateClip(clip.id) { it.copy(trimEnd = maxOf(value.toDouble(), it.trimStart + Clip.MIN_LENGTH)) }
    })

    SectionTitle("Tezlik")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Clip.SPEEDS.forEach { speed ->
            FilterChip(
                selected = clip.speed == speed,
                onClick = { editor.updateClip(clip.id) { it.copy(speed = speed) } },
                label = { Text(Format.speed(speed)) },
            )
        }
    }

    if (index < clips.lastIndex) {
        SectionTitle("Keyingi klipga o'tish")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransitionKind.entries.forEach { kind ->
                FilterChip(
                    selected = clip.transition == kind,
                    onClick = { editor.updateClip(clip.id) { it.copy(transition = kind) } },
                    label = { Text(kind.title) },
                )
            }
        }
        if (clip.transition != TransitionKind.NONE) {
            LabeledSlider("O'tish davomiyligi", clip.transitionDuration.toFloat(), 0.2f..1.5f, Format.seconds(clip.transitionDuration), { v ->
                editor.updateClip(clip.id) { it.copy(transitionDuration = v.toDouble()) }
            })
            HintText("O'tishni ko'rish uchun pauzada kliplar chegarasiga suring. Tayyor videoda to'liq chiqadi.")
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { editor.moveClip(clip.id, -1) }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("Chapga")
        }
        OutlinedButton(onClick = { editor.moveClip(clip.id, 1) }, enabled = index < clips.lastIndex) {
            Text("O'ngga")
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { editor.deleteClip(clip.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClipCard(number: Int, clip: Clip, thumbnail: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
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
            Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall)
            Text(Format.seconds(clip.outputDuration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// endregion

// region Ovoz

@Composable
private fun AudioPanel(editor: MontajEditor) {
    val state = editor.state
    val pickMusic = rememberMediaPicker(PickKind.AUDIO, multiple = false, onPicked = editor::importMusic)

    SectionTitle("Videoning asl ovozi")
    VolumeRow(state.originalVolume, editor::setOriginalVolume)

    SectionTitle("Fon musiqasi")
    val music = state.music
    if (music != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
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
private fun VoiceoverSection(editor: MontajEditor) {
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
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
        Column(
            Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, contentDescription = null)
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
private fun VolumeRow(volume: Float, onChange: (Float) -> Unit) {
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

@Composable
private fun TextPanel(editor: MontajEditor) {
    val state = editor.state
    var showAuto by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = editor::addOverlay) { Text("Matn qo'shish") }
        OutlinedButton(onClick = { showAuto = true }) { Text("Avto-subtitr") }
    }
    if (showAuto) AutoSubtitleDialog(editor) { showAuto = false }
    if (state.overlays.isEmpty()) {
        HintText("Matn joriy vaqtdan boshlab 3 soniya ko'rinadi. Vaqtini keyin o'zgartirish mumkin.")
    }
    state.overlays.forEach { overlay ->
        val selected = overlay.id == editor.selectedOverlayId
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f))
                .clickable { editor.selectedOverlayId = if (selected) null else overlay.id }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(overlay.text.ifBlank { "(bo'sh)" }, maxLines = 1)
            HintText("${Format.seconds(overlay.start)} – ${Format.seconds(overlay.end)} · ${overlay.position.title}")
            if (selected) {
                val maxTime = state.totalDuration.coerceAtLeast(0.5).toFloat()
                OutlinedTextField(
                    value = overlay.text,
                    onValueChange = { text -> editor.updateOverlay(overlay.id) { it.copy(text = text) } },
                    label = { Text("Matn") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSlider("Boshlanishi", overlay.start.toFloat(), 0f..maxTime, Format.seconds(overlay.start), { v ->
                    editor.updateOverlay(overlay.id) { it.copy(start = minOf(v.toDouble(), it.end - 0.2)) }
                })
                LabeledSlider("Tugashi", overlay.end.toFloat(), 0f..maxTime, Format.seconds(overlay.end), { v ->
                    editor.updateOverlay(overlay.id) { it.copy(end = maxOf(v.toDouble(), it.start + 0.2)) }
                })
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
                SwitchRow("Orqa fon (subtitr uslubi)", overlay.hasBackground) { on ->
                    editor.updateOverlay(overlay.id) { it.copy(hasBackground = on) }
                }
                TextButton(onClick = { editor.deleteOverlay(overlay.id) }) {
                    Text("Matnni o'chirish", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
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
private fun FilterPanel(editor: MontajEditor) {
    val source = editor.state.clips.firstOrNull()?.let { editor.thumbnails[it.id] }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VideoFilter.entries.forEach { filter ->
            val selected = editor.state.filter == filter
            val preview = remember(source, filter) {
                source?.let { image -> filter.matrix()?.let { Graphics.withColorMatrix(image, it) } ?: image }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { editor.setFilter(filter) }) {
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .size(width = 76.dp, height = 100.dp)
                        .clip(shape)
                        .border(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .background(Color.DarkGray),
                ) {
                    if (preview != null) {
                        Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
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

// endregion
