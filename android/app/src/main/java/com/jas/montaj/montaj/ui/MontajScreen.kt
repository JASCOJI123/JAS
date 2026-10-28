@file:OptIn(ExperimentalMaterial3Api::class)

package com.jas.montaj.montaj.ui

import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.jas.montaj.common.BusyOverlay
import com.jas.montaj.common.ErrorDialog
import com.jas.montaj.common.ExportDialog
import com.jas.montaj.common.ExportState
import com.jas.montaj.common.Formats
import com.jas.montaj.montaj.MontajComposer
import com.jas.montaj.montaj.MontajViewModel
import com.jas.montaj.montaj.TextOverlayPainter

private val panelTitles = listOf("Kliplar", "Ovoz", "Matn", "Filtr")

@Composable
fun MontajScreen(modifier: Modifier = Modifier, viewModel: MontajViewModel = viewModel()) {
    val context = LocalContext.current
    var panel by rememberSaveable { mutableIntStateOf(0) }
    val pickVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.importVideos(uris)
    }
    val openPicker = { pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Montaj") },
                    navigationIcon = {
                        IconButton(onClick = openPicker) { Icon(Icons.Filled.Add, contentDescription = "Video qo'shish") }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.export() },
                            enabled = viewModel.state.clips.isNotEmpty() && viewModel.exportState == ExportState.Idle,
                        ) { Text("Saqlash") }
                    },
                )
            },
        ) { padding ->
            if (viewModel.state.clips.isEmpty()) {
                EmptyState(Modifier.padding(padding), openPicker)
            } else {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Preview(viewModel)
                    Transport(viewModel)
                    TabRow(selectedTabIndex = panel) {
                        panelTitles.forEachIndexed { index, title ->
                            Tab(selected = panel == index, onClick = { panel = index }, text = { Text(title) })
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (panel) {
                            0 -> ClipsPanel(viewModel)
                            1 -> AudioPanel(viewModel)
                            2 -> TextPanel(viewModel)
                            else -> FilterPanel(viewModel)
                        }
                    }
                }
            }
        }
        if (viewModel.isImporting) BusyOverlay("Yuklanmoqda…")
        ExportDialog(viewModel.exportState, context) { viewModel.dismissExport() }
        ErrorDialog(viewModel.errorMessage) { viewModel.errorMessage = null }
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
private fun Preview(viewModel: MontajViewModel) {
    Box(
        Modifier.fillMaxWidth().height(330.dp).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.aspectRatio(MontajComposer.WIDTH / MontajComposer.HEIGHT.toFloat())) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        player = viewModel.player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Matnlar ham eksportdagi funksiya bilan, kichraytirib chiziladi.
            val time = viewModel.currentTime
            val active = viewModel.state.overlays.filter { time >= it.start && time < it.end }
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / MontajComposer.WIDTH
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(scale, scale)
                    active.forEach {
                        TextOverlayPainter.draw(native, it, MontajComposer.WIDTH.toFloat(), MontajComposer.HEIGHT.toFloat())
                    }
                    native.restore()
                }
            }
        }
    }
}

@Composable
private fun Transport(viewModel: MontajViewModel) {
    val total = viewModel.state.totalDuration
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.togglePlayback() }) {
            Icon(if (viewModel.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Ijro")
        }
        Slider(
            value = viewModel.currentTime.toFloat().coerceIn(0f, total.toFloat().coerceAtLeast(0.01f)),
            onValueChange = { viewModel.seek(it.toDouble()) },
            valueRange = 0f..total.toFloat().coerceAtLeast(0.01f),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${Formats.clock(viewModel.currentTime)} / ${Formats.clock(total)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
