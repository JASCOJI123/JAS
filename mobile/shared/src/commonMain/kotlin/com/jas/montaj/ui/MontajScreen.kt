package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.ExportState
import com.jas.montaj.montaj.Clip
import com.jas.montaj.montaj.MontajEditor
import com.jas.montaj.montaj.MontajState
import com.jas.montaj.montaj.TransitionKind
import com.jas.montaj.platform.PickKind
import com.jas.montaj.platform.PlatformBackHandler
import com.jas.montaj.platform.PlayerSurface
import com.jas.montaj.platform.rememberMediaPicker
import kotlin.math.ceil

/** Timeline'da nima tanlangan. */
private sealed interface Selection {
    data object None : Selection
    data class ClipItem(val id: String) : Selection
    data class Overlay(val id: String) : Selection
    data object Music : Selection
    data class Voice(val id: String) : Selection
}

/** Pastdan ochiladigan asbob paneli. */
private enum class Tool(val title: String) {
    AUDIO("Ovoz"),
    TEXT("Matn"),
    TEXT_EDIT("Matnni tahrirlash"),
    FILTER("Filtrlar"),
    SPEED("Tezlik va qirqish"),
    TRANSITION("O'tish effekti"),
}

/** CapCut uslubidagi Montaj muharriri. */
@Composable
fun MontajScreen(editor: MontajEditor, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var selection by remember { mutableStateOf<Selection>(Selection.None) }
    var tool by remember { mutableStateOf<Tool?>(null) }
    var transitionClipId by remember { mutableStateOf<String?>(null) }
    var pixelsPerSecond by remember { mutableStateOf(64.dp) }
    val pickVideos = rememberMediaPicker(PickKind.VIDEO, multiple = true, onPicked = editor::importVideos)
    val pickMusic = rememberMediaPicker(PickKind.AUDIO, multiple = false, onPicked = editor::importMusic)

    // Tanlangan element o'chirilsa — tanlovni tozalaymiz.
    val state = editor.state
    val current = selection
    val valid = when (current) {
        Selection.None -> true
        is Selection.ClipItem -> state.clips.any { it.id == current.id }
        is Selection.Overlay -> state.overlays.any { it.id == current.id }
        Selection.Music -> state.music != null
        is Selection.Voice -> state.voiceovers.any { it.id == current.id }
    }
    if (!valid) selection = Selection.None
    fun select(newSelection: Selection) {
        selection = newSelection
        editor.selectedClipId = (newSelection as? Selection.ClipItem)?.id
        editor.selectedOverlayId = (newSelection as? Selection.Overlay)?.id
    }

    PlatformBackHandler(enabled = true) {
        when {
            tool != null -> tool = null
            selection != Selection.None -> select(Selection.None)
            else -> onClose()
        }
    }

    Box(modifier.fillMaxSize().background(CapCut.Background)) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                title = editor.projectInfo.name,
                onClose = onClose,
                onExport = editor::export,
                exportEnabled = state.clips.isNotEmpty() && editor.exportState == ExportState.Idle,
            )
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF0B0B0B)), contentAlignment = Alignment.Center) {
                if (state.clips.isEmpty()) {
                    EmptyProject(pickVideos)
                } else {
                    Preview(editor)
                }
            }
            TransportBar(editor.currentTime, state.totalDuration, editor.isPlaying, editor::togglePlayback) {
                UndoRedoButtons(editor.history, editor::undo, editor::redo)
            }

            val activeTool = tool
            if (activeTool != null) {
                ToolPanel(activeTool.title, onDone = { tool = null }, modifier = Modifier.height(320.dp)) {
                    val clip = (selection as? Selection.ClipItem)?.let { s -> state.clips.firstOrNull { it.id == s.id } }
                    when (activeTool) {
                        Tool.AUDIO -> AudioPanel(editor)
                        Tool.TEXT -> TextToolPanel(editor) { id ->
                            select(Selection.Overlay(id))
                            tool = Tool.TEXT_EDIT
                        }
                        Tool.TEXT_EDIT -> state.overlays.firstOrNull { it.id == (selection as? Selection.Overlay)?.id }
                            ?.let { TextEditPanel(editor, it) } ?: HintText("Matn tanlanmagan")
                        Tool.FILTER -> FilterPanel(editor)
                        Tool.SPEED -> clip?.let { SpeedPanel(editor, it) } ?: HintText("Klip tanlanmagan")
                        Tool.TRANSITION -> state.clips.firstOrNull { it.id == transitionClipId }
                            ?.let { TransitionPanel(editor, it) } ?: HintText("Klip tanlanmagan")
                    }
                }
            } else if (state.clips.isNotEmpty()) {
                TimelineViewport(
                    currentTime = editor.currentTime,
                    duration = state.totalDuration,
                    pixelsPerSecond = pixelsPerSecond,
                    onSeek = editor::seek,
                    onZoom = { factor -> pixelsPerSecond = (pixelsPerSecond * factor).coerceIn(12.dp, 260.dp) },
                    modifier = Modifier.height(200.dp),
                ) { scope ->
                    MainTrack(
                        scope = scope,
                        editor = editor,
                        selectedId = (selection as? Selection.ClipItem)?.id,
                        onSelect = { select(Selection.ClipItem(it)) },
                        onTransition = { id ->
                            transitionClipId = id
                            tool = Tool.TRANSITION
                        },
                        onAdd = pickVideos,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextTrack(scope, editor, (selection as? Selection.Overlay)?.id, onSelect = { select(Selection.Overlay(it)) }) {
                        editor.addOverlay()
                        editor.selectedOverlayId?.let { id -> select(Selection.Overlay(id)) }
                        tool = Tool.TEXT_EDIT
                    }
                    Spacer(Modifier.height(6.dp))
                    AudioTrack(scope, editor, selection, onSelect = ::select, onAddMusic = pickMusic)
                }
                Toolbar(
                    editor = editor,
                    selection = selection,
                    onDeselect = { select(Selection.None) },
                    onTool = { tool = it },
                    onTransition = { id ->
                        transitionClipId = id
                        tool = Tool.TRANSITION
                    },
                    onAddVideo = pickVideos,
                    onReplaceMusic = pickMusic,
                )
            }
        }
        if (editor.isImporting) BusyOverlay("Yuklanmoqda…")
        editor.subtitleProgress?.let { BusyOverlay("Subtitr yaratilmoqda… ${(it * 100).toInt()}%") }
        ExportDialog(editor.exportState, onShare = editor::share, onDismiss = editor::dismissExport)
        ErrorDialog(editor.errorMessage) { editor.errorMessage = null }
    }
}

@Composable
private fun EmptyProject(onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(96.dp)
                .background(CapCut.Surface, RoundedCornerShape(20.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Video qo'shish", modifier = Modifier.size(48.dp)) }
        Text("Video qo'shish", style = MaterialTheme.typography.titleMedium)
        HintText("Galereyadan bir yoki bir nechta video tanlang")
    }
}

@Composable
private fun Preview(editor: MontajEditor) {
    Box(Modifier.fillMaxHeight().aspectRatio(MontajState.WIDTH / MontajState.HEIGHT.toFloat()).background(Color.Black)) {
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

// region Treklar

@Composable
private fun MainTrack(
    scope: TimelineScope,
    editor: MontajEditor,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onTransition: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val clips = editor.state.clips
    val starts = editor.state.clipStarts
    val total = editor.state.totalDuration
    val secondsPerPx = 1.0 / with(LocalDensity.current) { scope.pixelsPerSecond.toPx() }
    Box(Modifier.fillMaxWidth().height(58.dp)) {
        clips.forEachIndexed { index, clip ->
            val selected = clip.id == selectedId
            ClipBlock(
                clip = clip,
                thumbnail = editor.thumbnails[clip.id],
                selected = selected,
                width = scope.width(clip.outputDuration),
                modifier = Modifier.offset(x = scope.x(starts[index])),
                onClick = { onSelect(clip.id) },
            )
            if (selected) {
                TrimHandle(Modifier.offset(x = scope.x(starts[index]) - 14.dp)) { dxPx ->
                    val delta = dxPx * secondsPerPx * clip.speed
                    editor.updateClip(clip.id) { it.copy(trimStart = (it.trimStart + delta).coerceIn(0.0, it.trimEnd - Clip.MIN_LENGTH)) }
                }
                TrimHandle(Modifier.offset(x = scope.x(starts[index] + clip.outputDuration))) { dxPx ->
                    val delta = dxPx * secondsPerPx * clip.speed
                    editor.updateClip(clip.id) { it.copy(trimEnd = (it.trimEnd + delta).coerceIn(it.trimStart + Clip.MIN_LENGTH, it.durationSec)) }
                }
            }
            if (index < clips.lastIndex) {
                val active = clip.transition != TransitionKind.NONE
                Box(
                    Modifier
                        .offset(x = scope.x(starts[index] + clip.outputDuration) - 11.dp, y = 18.dp)
                        .size(22.dp)
                        .background(if (active) CapCut.Accent else Color.White, RoundedCornerShape(4.dp))
                        .clickable { onTransition(clip.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "O'tish", tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(
            Modifier
                .offset(x = scope.x(total) + 10.dp, y = 5.dp)
                .size(48.dp)
                .background(Color.White, RoundedCornerShape(6.dp))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Video qo'shish", tint = Color.Black) }
    }
}

/** Klip: kadrlar lentasi (thumbnail takrorlanadi), tanlanganda oq ramka. */
@Composable
private fun ClipBlock(clip: Clip, thumbnail: ImageBitmap?, selected: Boolean, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .padding(vertical = 3.dp)
            .width(width.coerceAtLeast(4.dp))
            .height(52.dp)
            .clip(shape)
            .background(CapCut.Track)
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, shape)
            .clickable(onClick = onClick),
    ) {
        if (thumbnail != null) {
            val frameWidth = 40.dp
            val count = ceil(width / frameWidth).toInt().coerceIn(1, 200)
            Row(Modifier.fillMaxSize().clipToBounds()) {
                repeat(count) {
                    Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(frameWidth).fillMaxHeight())
                }
            }
        }
        if (clip.speed != 1.0) {
            Text(
                com.jas.montaj.core.Format.speed(clip.speed),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
            )
        }
        Text(
            com.jas.montaj.core.Format.seconds(clip.outputDuration),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
        )
    }
}

/** Klip chetidagi oq tutqich: tortib qirqiladi. */
@Composable
internal fun TrimHandle(modifier: Modifier, height: Dp = 52.dp, onDrag: (Float) -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier
            .padding(vertical = 3.dp)
            .width(14.dp)
            .height(height)
            .background(Color.White, RoundedCornerShape(3.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, amount ->
                    change.consume()
                    drag(amount)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(2.dp).height(16.dp).background(Color.Black)) }
}

@Composable
private fun TextTrack(scope: TimelineScope, editor: MontajEditor, selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val overlays = editor.state.overlays
    val secondsPerPx = 1.0 / with(LocalDensity.current) { scope.pixelsPerSecond.toPx() }
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        if (overlays.isEmpty()) {
            TrackHint("+ Matn qo'shish", Modifier.offset(x = scope.x(0.0)), onAdd)
        }
        overlays.forEach { overlay ->
            val selected = overlay.id == selectedId
            TrackBar(
                label = overlay.text.ifBlank { "Matn" },
                color = CapCut.TextTrack,
                icon = Icons.Filled.TextFields,
                selected = selected,
                modifier = Modifier.offset(x = scope.x(overlay.start)),
                width = scope.width(overlay.end - overlay.start),
                onClick = { onSelect(overlay.id) },
            )
            if (selected) {
                TrimHandle(Modifier.offset(x = scope.x(overlay.start) - 14.dp), height = 24.dp) { dx ->
                    editor.updateOverlay(overlay.id) { it.copy(start = (it.start + dx * secondsPerPx).coerceIn(0.0, it.end - 0.2)) }
                }
                TrimHandle(Modifier.offset(x = scope.x(overlay.end)), height = 24.dp) { dx ->
                    editor.updateOverlay(overlay.id) { it.copy(end = (it.end + dx * secondsPerPx).coerceAtLeast(it.start + 0.2)) }
                }
            }
        }
    }
}

@Composable
private fun AudioTrack(scope: TimelineScope, editor: MontajEditor, selection: Selection, onSelect: (Selection) -> Unit, onAddMusic: () -> Unit) {
    val state = editor.state
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        val music = state.music
        if (music == null && state.voiceovers.isEmpty()) {
            TrackHint("+ Musiqa qo'shish", Modifier.offset(x = scope.x(0.0)), onAddMusic)
        }
        if (music != null) {
            TrackBar(
                label = music.name,
                color = CapCut.AudioTrack,
                icon = Icons.Filled.MusicNote,
                selected = selection == Selection.Music,
                modifier = Modifier.offset(x = scope.x(0.0)),
                width = scope.width(state.totalDuration),
                onClick = { onSelect(Selection.Music) },
            )
        }
        state.voiceovers.forEachIndexed { index, voice ->
            TrackBar(
                label = "Ovoz ${index + 1}",
                color = CapCut.VoiceTrack,
                icon = Icons.Filled.Mic,
                selected = selection == Selection.Voice(voice.id),
                modifier = Modifier.offset(x = scope.x(voice.startAt)),
                width = scope.width(voice.duration),
                onClick = { onSelect(Selection.Voice(voice.id)) },
            )
        }
    }
}

@Composable
internal fun TrackBar(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    width: Dp,
    height: Dp = 24.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .padding(vertical = 3.dp)
            .width(width.coerceAtLeast(6.dp))
            .height(height)
            .clip(shape)
            .background(color.copy(alpha = 0.85f))
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

// endregion

// region Pastki asboblar

@Composable
private fun Toolbar(
    editor: MontajEditor,
    selection: Selection,
    onDeselect: () -> Unit,
    onTool: (Tool) -> Unit,
    onTransition: (String) -> Unit,
    onAddVideo: () -> Unit,
    onReplaceMusic: () -> Unit,
) {
    when (selection) {
        Selection.None -> BottomToolbar {
            ToolButton(Icons.Filled.VideoLibrary, "Video") { onAddVideo() }
            ToolButton(Icons.Filled.MusicNote, "Ovoz") { onTool(Tool.AUDIO) }
            ToolButton(Icons.Filled.TextFields, "Matn") { onTool(Tool.TEXT) }
            ToolButton(Icons.Filled.FilterVintage, "Filtrlar") { onTool(Tool.FILTER) }
            ToolButton(Icons.Filled.ContentCut, "Bo'lish") {
                if (!editor.splitAtPlayhead()) editor.errorMessage = "Kursorni klip o'rtasiga qo'ying"
            }
        }
        is Selection.ClipItem -> {
            val clips = editor.state.clips
            val index = clips.indexOfFirst { it.id == selection.id }
            BottomToolbar(onBack = onDeselect) {
                ToolButton(Icons.Filled.ContentCut, "Bo'lish") {
                    if (!editor.splitAtPlayhead()) editor.errorMessage = "Kursorni klip o'rtasiga qo'ying"
                }
                ToolButton(Icons.Filled.Timer, "Tezlik") { onTool(Tool.SPEED) }
                ToolButton(Icons.Filled.SwapHoriz, "O'tish", enabled = index in 0 until clips.lastIndex) { onTransition(selection.id) }
                ToolButton(Icons.AutoMirrored.Filled.VolumeUp, "Ovoz") { onTool(Tool.AUDIO) }
                ToolButton(Icons.Filled.FilterVintage, "Filtr") { onTool(Tool.FILTER) }
                ToolButton(Icons.AutoMirrored.Filled.ArrowBack, "Chapga", enabled = index > 0) { editor.moveClip(selection.id, -1) }
                ToolButton(Icons.AutoMirrored.Filled.ArrowForward, "O'ngga", enabled = index < clips.lastIndex) { editor.moveClip(selection.id, 1) }
                ToolButton(Icons.Filled.ContentCopy, "Nusxa") { editor.duplicateClip(selection.id) }
                ToolButton(Icons.Filled.Delete, "O'chirish", tint = MaterialTheme.colorScheme.error) { editor.deleteClip(selection.id) }
            }
        }
        is Selection.Overlay -> BottomToolbar(onBack = onDeselect) {
            ToolButton(Icons.Filled.Edit, "Tahrirlash") { onTool(Tool.TEXT_EDIT) }
            ToolButton(Icons.Filled.ContentCopy, "Nusxa") { editor.duplicateOverlay(selection.id) }
            ToolButton(Icons.Filled.Delete, "O'chirish", tint = MaterialTheme.colorScheme.error) { editor.deleteOverlay(selection.id) }
        }
        Selection.Music -> BottomToolbar(onBack = onDeselect) {
            ToolButton(Icons.AutoMirrored.Filled.VolumeUp, "Balandlik") { onTool(Tool.AUDIO) }
            ToolButton(Icons.Filled.SwapHoriz, "Almashtirish") { onReplaceMusic() }
            ToolButton(Icons.Filled.Delete, "O'chirish", tint = MaterialTheme.colorScheme.error) { editor.removeMusic() }
        }
        is Selection.Voice -> BottomToolbar(onBack = onDeselect) {
            ToolButton(Icons.AutoMirrored.Filled.VolumeUp, "Balandlik") { onTool(Tool.AUDIO) }
            ToolButton(Icons.Filled.Delete, "O'chirish", tint = MaterialTheme.colorScheme.error) { editor.deleteVoiceover(selection.id) }
        }
    }
}

// endregion
