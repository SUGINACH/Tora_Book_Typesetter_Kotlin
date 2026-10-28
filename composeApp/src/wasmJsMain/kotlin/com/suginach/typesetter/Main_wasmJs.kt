package com.suginach.typesetter

import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.suginach.typesetter.ui.App
import org.jetbrains.skiko.wasm.onWasmReady

fun main() {
    onWasmReady {
        CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
            App()
        }
    }
}
