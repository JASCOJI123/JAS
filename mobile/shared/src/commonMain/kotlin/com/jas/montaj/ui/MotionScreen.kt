@file:OptIn(ExperimentalMaterial3Api::class)

package com.jas.montaj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlin.math.roundToInt
import com.jas.montaj.core.ExportState
import com.jas.montaj.core.Format
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.platform.PickKind
import com.jas.montaj.platform.rememberMediaPicker
import com.jas.montaj.motion.MotionTemplate
import com.jas.montaj.motion.ShapeKind
import com.jas.montaj.motion.Vec2
import com.jas.montaj.motion.toParentSpace
import com.jas.montaj.motion.valueAt
import com.jas.montaj.motion.worldTransform

@Composable
fun MotionScreen(editor: MotionEditor, modifier: Modifier = Modifier) {
    var showSettings by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val pickVideo = rememberMediaPicker(PickKind.VIDEO, multiple = false, onPicked = editor::addVideos)
    val pickImage = rememberMediaPicker(PickKind.IMAGE, multiple = false, onPicked = editor::addImages)

    Box(modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(editor.projectInfo.name, maxLines = 1) },
                    navigationIcon = {
                        ProjectsButton {
                            editor.refreshProjects()
                            showProjects = true
                        }
                    },
                    actions = {
                        UndoRedoButtons(editor.history, editor::undo, editor::redo)
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Sozlamalar") }
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "Qatlam qo'shish", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                DropdownMenuItem(text = { Text("Matn") }, onClick = { showAddMenu = false; editor.addText() })
                                DropdownMenuItem(text = { Text("Video") }, onClick = {
                                    showAddMenu = false
                                    pickVideo()
                                })
                                DropdownMenuItem(text = { Text("Rasm") }, onClick = {
                                    showAddMenu = false
                                    pickImage()
                                })
                                DropdownMenuItem(text = { Text("Solid (rangli fon)") }, onClick = { showAddMenu = false; editor.addSolid() })
                                HorizontalDivider()
                                MotionTemplate.entries.forEach { template ->
                                    DropdownMenuItem(text = { Text("Shablon: ${template.title}") }, onClick = { showAddMenu = false; editor.addTemplate(template) })
                                }
                                HorizontalDivider()
                                ShapeKind.entries.forEach { kind ->
                                    DropdownMenuItem(text = { Text("Shakl: ${kind.title}") }, onClick = { showAddMenu = false; editor.addShape(kind) })
                                }
                            }
                        }
                        TextButton(onClick = { editor.export() }, enabled = editor.exportState == ExportState.Idle) {
                            Text("Saqlash")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                MotionPreview(editor)
                Transport(editor)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LayerTimeline(editor)
                    val layer = editor.selectedLayer
                    if (layer != null) {
                        LayerInspector(editor, layer)
                    } else if (editor.project.layers.isEmpty()) {
                        Text("Qatlam qo'shing", style = MaterialTheme.typography.titleMedium)
                        HintText(
                            "Yuqoridagi ⊕ tugmasi orqali matn, video, rasm yoki shakl qo'shing. Xususiyat yonidagi ⏱ tugmasi " +
                                "keyframe qo'yadi: vaqtni surib, qiymatni o'zgartiring — animatsiya tayyor.",
                        )
                    }
                }
            }
        }
        if (editor.isImporting) BusyOverlay("Yuklanmoqda…")
        if (showSettings) ProjectSettingsDialog(editor) { showSettings = false }
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
        ExportDialog(editor.exportState, onShare = editor::share, onDismiss = editor::dismissExport)
        ErrorDialog(editor.errorMessage) { editor.errorMessage = null }
    }
}

@Composable
private fun MotionPreview(editor: MotionEditor) {
    val project = editor.project
    val currentViewModel by rememberUpdatedState(editor)
    Box(
        Modifier.fillMaxWidth().height(330.dp).background(Color(0xFF141414)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
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
private fun Transport(editor: MotionEditor) {
    val duration = editor.project.duration
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { editor.togglePlayback() }) {
            Icon(if (editor.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Ijro")
        }
        Slider(
            value = editor.currentTime.toFloat(),
            onValueChange = { editor.seek(it.toDouble()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(0.1f),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${Format.preciseClock(editor.currentTime)} / ${Format.preciseClock(duration)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
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
