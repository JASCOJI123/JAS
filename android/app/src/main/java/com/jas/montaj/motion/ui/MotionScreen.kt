@file:OptIn(ExperimentalMaterial3Api::class)

package com.jas.montaj.motion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jas.montaj.common.BusyOverlay
import com.jas.montaj.common.ColorPaletteRow
import com.jas.montaj.common.ErrorDialog
import com.jas.montaj.common.ExportDialog
import com.jas.montaj.common.ExportState
import com.jas.montaj.common.Formats
import com.jas.montaj.common.HintText
import com.jas.montaj.common.LabeledSlider
import com.jas.montaj.motion.MotionViewModel
import com.jas.montaj.motion.model.ShapeKind
import com.jas.montaj.motion.model.Vec2
import com.jas.montaj.motion.model.valueAt

@Composable
fun MotionScreen(modifier: Modifier = Modifier, viewModel: MotionViewModel = viewModel()) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addVideo)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addImage)
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Motion") },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Loyiha") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Filled.AddCircle, contentDescription = "Qatlam qo'shish", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                DropdownMenuItem(text = { Text("Matn") }, onClick = { showAddMenu = false; viewModel.addText() })
                                DropdownMenuItem(text = { Text("Video") }, onClick = {
                                    showAddMenu = false
                                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                })
                                DropdownMenuItem(text = { Text("Rasm") }, onClick = {
                                    showAddMenu = false
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                DropdownMenuItem(text = { Text("Solid (rangli fon)") }, onClick = { showAddMenu = false; viewModel.addSolid() })
                                HorizontalDivider()
                                ShapeKind.entries.forEach { kind ->
                                    DropdownMenuItem(text = { Text("Shakl: ${kind.title}") }, onClick = { showAddMenu = false; viewModel.addShape(kind) })
                                }
                            }
                        }
                        TextButton(onClick = { viewModel.export() }, enabled = viewModel.exportState == ExportState.Idle) {
                            Text("Saqlash")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                MotionPreview(viewModel)
                Transport(viewModel)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LayerTimeline(viewModel)
                    val layer = viewModel.selectedLayer
                    if (layer != null) {
                        LayerInspector(viewModel, layer)
                    } else if (viewModel.project.layers.isEmpty()) {
                        Text("Qatlam qo'shing", style = MaterialTheme.typography.titleMedium)
                        HintText(
                            "Yuqoridagi ⊕ tugmasi orqali matn, video, rasm yoki shakl qo'shing. Xususiyat yonidagi ⏱ tugmasi " +
                                "keyframe qo'yadi: vaqtni surib, qiymatni o'zgartiring — animatsiya tayyor.",
                        )
                    }
                }
            }
        }
        if (viewModel.isImporting) BusyOverlay("Yuklanmoqda…")
        if (showSettings) ProjectSettingsDialog(viewModel) { showSettings = false }
        ExportDialog(viewModel.exportState, context) { viewModel.dismissExport() }
        ErrorDialog(viewModel.errorMessage) { viewModel.errorMessage = null }
    }
}

@Composable
private fun MotionPreview(viewModel: MotionViewModel) {
    val project = viewModel.project
    val currentViewModel by rememberUpdatedState(viewModel)
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
                        vm.updateLayer(layer.id) { current ->
                            val position = current.position.valueAt(time)
                            current.copy(
                                position = current.position.set(Vec2(position.x + pan.x * pixelsPerPoint, position.y + pan.y * pixelsPerPoint), time),
                                scale = current.scale.set((current.scale.valueAt(time) * zoom).coerceAtLeast(0.02), time),
                                rotation = current.rotation.set(current.rotation.valueAt(time) + rotation, time),
                            )
                        }
                    }
                },
        ) {
            viewModel.previewFrame?.let { frame ->
                Image(frame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            val layer = viewModel.selectedLayer
            if (layer != null && layer.isActive(viewModel.currentTime)) {
                val time = viewModel.currentTime
                val (contentWidth, contentHeight) = viewModel.contentSize(layer)
                val position = layer.position.valueAt(time)
                val scale = layer.scale.valueAt(time).toFloat()
                val rotation = layer.rotation.valueAt(time).toFloat()
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
private fun Transport(viewModel: MotionViewModel) {
    val duration = viewModel.project.duration
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.togglePlayback() }) {
            Icon(if (viewModel.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Ijro")
        }
        Slider(
            value = viewModel.currentTime.toFloat(),
            onValueChange = { viewModel.seek(it.toDouble()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(0.1f),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${Formats.preciseClock(viewModel.currentTime)} / ${Formats.preciseClock(duration)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ProjectSettingsDialog(viewModel: MotionViewModel, onDismiss: () -> Unit) {
    val project = viewModel.project
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loyiha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledSlider(
                    title = "Davomiylik",
                    value = project.duration.toFloat(),
                    range = 1f..60f,
                    valueText = Formats.preciseClock(project.duration),
                    onChange = { value -> viewModel.updateProject { it.copy(duration = (Math.round(value * 2) / 2.0)) } },
                )
                ColorPaletteRow("Fon rangi", project.background) { color -> viewModel.updateProject { it.copy(background = color) } }
                HintText("Kadr: 1080×1920 (9:16), 30 kadr/soniya")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tayyor") } },
    )
}
