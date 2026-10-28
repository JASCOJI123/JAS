package com.jas.montaj.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF5E3A)

@Composable
fun MontajTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Color(0xFFFFC83A),
            background = Color.Black,
            surface = Color(0xFF121212),
        ),
        content = content,
    )
}
