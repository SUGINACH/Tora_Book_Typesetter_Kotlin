package com.suginach.typesetter

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
        App()
    }
}
