package com.suginach.typesetter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.suginach.typesetter.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "מערכת עימוד ספרים תורניים"
    ) {
        App()
    }
}
