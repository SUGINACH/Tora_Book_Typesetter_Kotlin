package com.suginach.typesetter

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.suginach.typesetter.ui.App
import kotlinx.browser.document
import kotlin.experimental.ExperimentalNativeApi

/**
 * ממתין לטעינת כל הגופנים (document.fonts.ready) לפני הפעלת Compose.
 * חיוני כדי ש-Skiko ימצא את הגופן Frank-Ruehl ולא יציג סימונים (tofu).
 */
@OptIn(ExperimentalNativeApi::class)
@JsFun("(callback) => { if (document.fonts && document.fonts.ready) { document.fonts.ready.then(function() { callback(); }); } else { callback(); } }")
private external fun waitForFonts(callback: () -> Unit)

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
