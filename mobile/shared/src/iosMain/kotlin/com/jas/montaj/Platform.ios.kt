package com.jas.montaj

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS ilovasi (Swift) shu funksiya orqali umumiy Compose UI'ni ochadi. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
