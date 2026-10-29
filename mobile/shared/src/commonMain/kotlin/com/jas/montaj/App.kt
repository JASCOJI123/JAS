package com.jas.montaj

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.jas.montaj.core.ProjectKind
import com.jas.montaj.core.ProjectStore
import com.jas.montaj.core.SettingsStore
import com.jas.montaj.montaj.MontajEditor
import com.jas.montaj.motion.MotionEditor
import com.jas.montaj.platform.mediaBackend
import com.jas.montaj.platform.storage
import com.jas.montaj.ui.CapCut
import com.jas.montaj.ui.HomeScreen
import com.jas.montaj.ui.MontajScreen
import com.jas.montaj.ui.MontajTheme
import com.jas.montaj.ui.MotionScreen

private enum class Screen { HOME, MONTAJ, MOTION }

/** Ilovaning ildizi — iOS va Android bir xil UI'ni ko'rsatadi. */
@Composable
fun App() {
    val fonts = LocalFontFamilyResolver.current
    val media = remember { mediaBackend() }
    val store = remember { ProjectStore(storage()) }
    val settings = remember { SettingsStore(storage()) }
    val montaj = remember { MontajEditor(media, fonts, store, settings) }
    val motion = remember { MotionEditor(media, fonts, store) }
    DisposableEffect(Unit) {
        onDispose {
            montaj.dispose()
            motion.dispose()
        }
    }

    var screen by remember { mutableStateOf(Screen.HOME) }
    MontajTheme {
        Surface(Modifier.fillMaxSize(), color = CapCut.Background) {
            val content = Modifier.fillMaxSize().safeDrawingPadding()
            when (screen) {
                Screen.HOME -> HomeScreen(
                    store = store,
                    media = media,
                    onNewMontaj = {
                        montaj.newProject()
                        screen = Screen.MONTAJ
                    },
                    onNewMotion = {
                        motion.newProject()
                        screen = Screen.MOTION
                    },
                    onOpen = { project ->
                        if (project.kind == ProjectKind.MONTAJ) {
                            montaj.openProject(project.id)
                            screen = Screen.MONTAJ
                        } else {
                            motion.openProject(project.id)
                            screen = Screen.MOTION
                        }
                    },
                )
                Screen.MONTAJ -> MontajScreen(montaj, onClose = {
                    montaj.closeProject()
                    screen = Screen.HOME
                }, modifier = content)
                Screen.MOTION -> MotionScreen(motion, onClose = {
                    motion.closeProject()
                    screen = Screen.HOME
                }, modifier = content)
            }
        }
    }
}
