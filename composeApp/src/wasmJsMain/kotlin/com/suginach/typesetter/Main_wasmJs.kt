package com.suginach.typesetter

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.Promise

/**
 * ממתין לטעינת כל הגופנים (document.fonts.ready) לפני הפעלת Compose.
 * חיוני כדי ש-Skiko ימצא את הגופן Frank-Ruehl ולא יציג סימונים (tofu).
 */
private fun waitForFonts(callback: () -> Unit) {
    val fonts = document.asDynamic().fonts
    val ready = fonts?.ready
    if (ready != null && ready is Promise<*>) {
        ready.then { callback() }
    } else {
        callback()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    waitForFonts { startCompose() }
}

private fun startCompose() {
    document.getElementById("loading")?.remove()
    CanvasBasedWindow("מערכת עימוד ספרים תורניים") {
        App()
    }
}
