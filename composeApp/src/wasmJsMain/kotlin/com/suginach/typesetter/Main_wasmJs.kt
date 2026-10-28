package com.suginach.typesetter

import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App
import org.jetbrains.skiko.wasm.ExperimentalWasmApi

@OptIn(ExperimentalWasmApi::class)
fun main() {
    CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
        App()
    }
}
