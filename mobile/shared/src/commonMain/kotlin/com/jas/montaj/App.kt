package com.jas.montaj

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF5E3A))) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Montaj — ${platformName()}")
        }
    }
}

expect fun platformName(): String
