package com.jas.montaj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jas.montaj.core.History
import com.jas.montaj.core.ProjectInfo

/** Undo / Redo tugmalari (ikkala rejimda bir xil). */
@Composable
fun <T> UndoRedoButtons(history: History<T>, onUndo: () -> Unit, onRedo: () -> Unit) {
    IconButton(onClick = onUndo, enabled = history.canUndo) {
        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Orqaga qaytarish")
    }
    IconButton(onClick = onRedo, enabled = history.canRedo) {
        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Qaytadan bajarish")
    }
}

@Composable
fun ProjectsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Folder, contentDescription = "Loyihalar") }
}

/**
 * Loyihalar oynasi: joriy loyiha nomi, saqlangan loyihalar ro'yxati, yangi loyiha.
 * Hammasi avtomatik saqlanadi — alohida "saqlash" tugmasi kerak emas.
 */
@Composable
fun ProjectsDialog(
    current: ProjectInfo,
    projects: List<ProjectInfo>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(current.id) { mutableStateOf(current.name) }
    var confirmDelete by remember { mutableStateOf<ProjectInfo?>(null) }

    AlertDialog(
        onDismissRequest = {
            onRename(name)
            onDismiss()
        },
        title = { Text("Loyihalar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Joriy loyiha nomi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HintText("Loyiha avtomatik saqlanadi.")
                Button(onClick = {
                    onRename(name)
                    onNew()
                }) { Text("Yangi loyiha") }
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    projects.forEach { project ->
                        val isCurrent = project.id == current.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.12f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = !isCurrent) {
                                    onRename(name)
                                    onOpen(project.id)
                                }
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (isCurrent) "${project.name} (ochiq)" else project.name,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                maxLines = 1,
                            )
                            IconButton(onClick = { confirmDelete = project }) {
                                Icon(Icons.Filled.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (projects.isEmpty()) HintText("Hali saqlangan loyiha yo'q.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(name)
                onDismiss()
            }) { Text("Yopish") }
        },
    )

    confirmDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Loyihani o'chirasizmi?") },
            text = { Text("\"${project.name}\" butunlay o'chiriladi.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(project.id)
                    confirmDelete = null
                }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Bekor qilish") } },
        )
    }
}
