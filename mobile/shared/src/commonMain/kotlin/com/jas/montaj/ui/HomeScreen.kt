@file:OptIn(ExperimentalFoundationApi::class)

package com.jas.montaj.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jas.montaj.core.Format
import com.jas.montaj.core.ProjectInfo
import com.jas.montaj.core.ProjectKind
import com.jas.montaj.core.ProjectStore
import com.jas.montaj.platform.MediaBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CapCut uslubidagi bosh ekran: yangi loyiha tugmalari va saqlangan loyihalar. */
@Composable
fun HomeScreen(
    store: ProjectStore,
    media: MediaBackend,
    onNewMontaj: () -> Unit,
    onNewMotion: () -> Unit,
    onOpen: (ProjectInfo) -> Unit,
) {
    var projects by remember { mutableStateOf(emptyList<ProjectInfo>()) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(version) { projects = withContext(Dispatchers.Default) { store.listAll() } }
    var renaming by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(CapCut.Background).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Montaj", fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF20D5EC), Color(0xFF3B6CFF))))
                    .clickable(onClick = onNewMontaj),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Yangi loyiha", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("Motion", "Animatsiya, AE", Icons.Filled.AutoAwesome, Modifier.weight(1f), onNewMotion)
                QuickCard("Montaj", "Video tahrir", Icons.Filled.Movie, Modifier.weight(1f), onNewMontaj)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loyihalarim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${projects.size}", color = CapCut.Muted)
            }
        }
        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HintText("Hali loyiha yo'q. \"Yangi loyiha\" ni bosing.")
            }
        }
        items(projects, key = { it.id }) { project ->
            ProjectCard(
                project = project,
                media = media,
                onOpen = { onOpen(project) },
                onRename = { renaming = project },
                onDelete = { deleting = project },
            )
        }
    }

    renaming?.let { project ->
        var name by remember(project.id) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Nomini o'zgartirish") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    store.rename(project.id, name.ifBlank { project.name })
                    renaming = null
                    version++
                }) { Text("Saqlash") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Bekor qilish") } },
        )
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Loyihani o'chirasizmi?") },
            text = { Text("\"${project.name}\" butunlay o'chiriladi.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(project.id)
                    deleting = null
                    version++
                }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Bekor qilish") } },
        )
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CapCut.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CapCut.Accent)
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CapCut.Muted)
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectInfo, media: MediaBackend, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val cover by produceState<ImageBitmap?>(null, project.coverPath) {
        val path = project.coverPath ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { if (project.coverIsVideo) media.thumbnail(path, 240) else media.loadImage(path) }.getOrNull()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (project.kind == ProjectKind.MOTION) {
                        Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFFE2C55)))
                    } else {
                        Brush.linearGradient(listOf(CapCut.Surface, CapCut.Track))
                    },
                )
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            cover?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (cover == null) {
                Icon(
                    if (project.kind == ProjectKind.MOTION) Icons.Filled.AutoAwesome else Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
            if (project.kind == ProjectKind.MOTION) {
                Text(
                    "Motion",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                )
            }
            Text(
                Format.clock(project.durationSec),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ochish") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Nomini o'zgartirish") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("O'chirish", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
        Text(project.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
