package com.jas.montaj

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.jas.montaj.montaj.MontajEditor
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.core.ProjectStore
import com.jas.montaj.core.SettingsStore
import com.jas.montaj.platform.mediaBackend
import com.jas.montaj.platform.storage
import com.jas.montaj.ui.MontajScreen
import com.jas.montaj.ui.MontajTheme
import com.jas.montaj.ui.MotionScreen

/** Ilovaning ildizi — iOS va Android bir xil UI'ni ko'rsatadi. */
@Composable
fun App() {
    val fonts = LocalFontFamilyResolver.current
    val store = remember { ProjectStore(storage()) }
    val settings = remember { SettingsStore(storage()) }
    val montaj = remember { MontajEditor(mediaBackend(), fonts, store, settings) }
    val motion = remember { MotionEditor(mediaBackend(), fonts, store) }
    DisposableEffect(Unit) {
        onDispose {
            montaj.dispose()
            motion.dispose()
        }
    }

    MontajTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                        label = { Text("Montaj") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        label = { Text("Motion") },
                    )
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                0 -> MontajScreen(montaj, modifier)
                else -> MotionScreen(motion, modifier)
            }
        }
    }
}
