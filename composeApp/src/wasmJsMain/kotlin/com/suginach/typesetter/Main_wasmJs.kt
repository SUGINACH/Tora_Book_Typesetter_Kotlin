package com.suginach.typesetter

import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App

@Suppress("EXPERIMENTAL_API_USAGE")
fun main() {
    CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
        App()
    }
}
