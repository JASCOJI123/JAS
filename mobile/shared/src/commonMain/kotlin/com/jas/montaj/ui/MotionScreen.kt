@file:OptIn(ExperimentalLayoutApi::class)

package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.ExportState
import com.jas.montaj.core.Format
import com.jas.montaj.motion.AnimationPreset
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.motion.MotionTemplate
import com.jas.montaj.motion.ShapeKind
import com.jas.montaj.motion.Vec2
import com.jas.montaj.motion.toParentSpace
import com.jas.montaj.motion.valueAt
import com.jas.montaj.motion.worldTransform
import com.jas.montaj.platform.PickKind
import com.jas.montaj.platform.PlatformBackHandler
import com.jas.montaj.platform.rememberMediaPicker
import kotlin.math.roundToInt

/** Pastdan ochiladigan Motion asbob paneli. */
private enum class MotionTool(val title: String) {
    PRESET("Animatsiya presetlari"),
    TRANSFORM("Transform va keyframe"),
    CONTENT("Kontent"),
    EFFECTS("Effektlar"),
    MASK("Mask"),
    LINK("Bog'lash: parent, wiggle, loop"),
    LAYER("Qatlam"),
    SHAPES("Shakl qo'shish"),
    TEMPLATES("Shablonlar"),
}

/** CapCut uslubidagi Motion muharriri (mini After Effects). */
@Composable
fun MotionScreen(editor: MotionEditor, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var tool by remember { mutableStateOf<MotionTool?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var pixelsPerSecond by remember { mutableStateOf(48.dp) }
    val pickVideo = rememberMediaPicker(PickKind.VIDEO, multiple = false, onPicked = editor::addVideos)
    val pickImage = rememberMediaPicker(PickKind.IMAGE, multiple = false, onPicked = editor::addImages)
    val layer = editor.selectedLayer

    PlatformBackHandler(enabled = true) {
        when {
            tool != null -> tool = null
            editor.selectedLayerId != null -> editor.selectedLayerId = null
            else -> onClose()
        }
    }

    Box(modifier.fillMaxSize().background(CapCut.Background)) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                title = editor.projectInfo.name,
                onClose = onClose,
                onExport = editor::export,
                exportEnabled = editor.exportState == ExportState.Idle,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) { MotionPreview(editor) }
            TransportBar(editor.currentTime, editor.project.duration, editor.isPlaying, editor::togglePlayback) {
                UndoRedoButtons(editor.history, editor::undo, editor::redo)
            }

            val activeTool = tool
            if (activeTool != null) {
                ToolPanel(activeTool.title, onDone = { tool = null }, modifier = Modifier.height(330.dp)) {
                    val update: ((com.jas.montaj.motion.MotionLayer) -> com.jas.montaj.motion.MotionLayer) -> Unit = { change ->
                        layer?.let { editor.updateLayer(it.id, change) }
                    }
                    val time = editor.currentTime
                    when (activeTool) {
                        MotionTool.SHAPES -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShapeKind.entries.forEach { kind ->
                                AssistChip(onClick = { editor.addShape(kind); tool = null }, label = { Text(kind.title) })
                            }
                        }
                        MotionTool.TEMPLATES -> MotionTemplate.entries.forEach { template ->
                            OutlinedButton(onClick = { editor.addTemplate(template); tool = null }, modifier = Modifier.fillMaxWidth()) { Text(template.title) }
                        }
                        else -> if (layer == null) {
                            HintText("Qatlam tanlanmagan")
                        } else {
                            when (activeTool) {
                                MotionTool.PRESET -> {
                                    HintText("Preset joriy vaqtdan boshlab keyframe'lar qo'yadi.")
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AnimationPreset.entries.forEach { preset ->
                                            AssistChip(onClick = { editor.applyPreset(layer.id, preset) }, label = { Text(preset.title) })
                                        }
                                    }
                                }
                                MotionTool.TRANSFORM -> TransformTab(layer, time, editor.project.width.toDouble(), editor.project.height.toDouble(), update) { preset ->
                                    editor.applyPreset(layer.id, preset)
                                }
                                MotionTool.CONTENT -> ContentTab(layer, update)
                                MotionTool.EFFECTS -> EffectsTab(layer, time, update)
                                MotionTool.MASK -> MaskTab(layer, update)
                                MotionTool.LINK -> LinkTab(editor, layer, update)
                                MotionTool.LAYER -> LayerTab(editor, layer, update)
                                else -> Unit
                            }
                        }
                    }
                }
            } else {
                MotionLayerTimeline(editor, pixelsPerSecond, onZoom = { factor -> pixelsPerSecond = (pixelsPerSecond * factor).coerceIn(10.dp, 240.dp) })
                if (layer == null) {
                    BottomToolbar {
                        ToolButton(Icons.Filled.TextFields, "Matn") { editor.addText() }
                        ToolButton(Icons.Filled.Movie, "Video") { pickVideo() }
                        ToolButton(Icons.Filled.Image, "Rasm") { pickImage() }
                        ToolButton(Icons.Filled.Category, "Shakl") { tool = MotionTool.SHAPES }
                        ToolButton(Icons.Filled.Gradient, "Solid") { editor.addSolid() }
                        ToolButton(Icons.Filled.Dashboard, "Shablon") { tool = MotionTool.TEMPLATES }
                        ToolButton(Icons.Filled.Settings, "Loyiha") { showSettings = true }
                    }
                } else {
                    BottomToolbar(onBack = { editor.selectedLayerId = null }) {
                        ToolButton(Icons.Filled.AutoAwesome, "Animatsiya") { tool = MotionTool.PRESET }
                        ToolButton(Icons.Filled.OpenWith, "Transform") { tool = MotionTool.TRANSFORM }
                        ToolButton(Icons.Filled.Tune, "Kontent") { tool = MotionTool.CONTENT }
                        ToolButton(Icons.Filled.WbSunny, "Effekt") { tool = MotionTool.EFFECTS }
                        ToolButton(Icons.Filled.SquareFoot, "Mask") { tool = MotionTool.MASK }
                        ToolButton(Icons.Filled.Link, "Bog'lash") { tool = MotionTool.LINK }
                        ToolButton(Icons.Filled.Layers, "Qatlam") { tool = MotionTool.LAYER }
                        ToolButton(Icons.Filled.ContentCopy, "Nusxa") { editor.duplicateLayer(layer.id) }
                        ToolButton(Icons.Filled.Delete, "O'chirish", tint = MaterialTheme.colorScheme.error) { editor.deleteLayer(layer.id) }
                    }
                }
            }
        }
        if (editor.isImporting) BusyOverlay("Yuklanmoqda…")
        if (showSettings) ProjectSettingsDialog(editor) { showSettings = false }
        ExportDialog(editor.exportState, onShare = editor::share, onDismiss = editor::dismissExport)
        ErrorDialog(editor.errorMessage) { editor.errorMessage = null }
    }
}

@Composable
private fun MotionPreview(editor: MotionEditor) {
    val project = editor.project
    val currentViewModel by rememberUpdatedState(editor)
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0B0B)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(project.width / project.height.toFloat())
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val vm = currentViewModel
                        val layer = vm.selectedLayer ?: return@detectTransformGestures
                        val time = vm.currentTime
                        val pixelsPerPoint = vm.project.width / size.width.toDouble()
                        // Ota-qatlam bo'lsa, siljish uning koordinatalariga aylantiriladi.
                        val delta = vm.project.toParentSpace(layer, Vec2(pan.x * pixelsPerPoint, pan.y * pixelsPerPoint), time)
                        vm.updateLayer(layer.id) { current ->
                            val position = current.position.valueAt(time)
                            current.copy(
                                position = current.position.set(Vec2(position.x + delta.x, position.y + delta.y), time),
                                scale = current.scale.set((current.scale.valueAt(time) * zoom).coerceAtLeast(0.02), time),
                                rotation = current.rotation.set(current.rotation.valueAt(time) + rotation, time),
                            )
                        }
                    }
                },
        ) {
            editor.previewFrame?.let { frame ->
                Image(frame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            val layer = editor.selectedLayer
            if (layer != null && layer.isActive(editor.currentTime)) {
                val time = editor.currentTime
                val (contentWidth, contentHeight) = editor.contentSize(layer)
                val transform = project.worldTransform(layer, time)
                val position = transform.position
                val scale = transform.scale.toFloat()
                val rotation = transform.rotation.toFloat()
                val accent = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val k = size.width / project.width
                    val center = Offset((position.x * k).toFloat(), (position.y * k).toFloat())
                    val boxSize = Size(contentWidth * scale * k, contentHeight * scale * k)
                    rotate(rotation, pivot = center) {
                        drawRect(
                            color = accent,
                            topLeft = Offset(center.x - boxSize.width / 2, center.y - boxSize.height / 2),
                            size = boxSize,
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectSettingsDialog(editor: MotionEditor, onDismiss: () -> Unit) {
    val project = editor.project
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loyiha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledSlider(
                    title = "Davomiylik",
                    value = project.duration.toFloat(),
                    range = 1f..60f,
                    valueText = Format.preciseClock(project.duration),
                    onChange = { value -> editor.updateProject { it.copy(duration = (value * 2).roundToInt() / 2.0) } },
                )
                ColorPaletteRow("Fon rangi", project.background) { color -> editor.updateProject { it.copy(background = color) } }
                HintText("Kadr: 1080×1920 (9:16), 30 kadr/soniya")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tayyor") } },
    )
}
