package com.suginach.typesetter

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.getElementById("loading")?.remove()
    CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
        App()
    }
}
