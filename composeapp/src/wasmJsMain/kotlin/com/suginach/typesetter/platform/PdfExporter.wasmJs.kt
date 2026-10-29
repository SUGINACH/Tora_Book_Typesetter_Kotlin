package com.suginach.typesetter.platform

import kotlinx.browser.window

/**
 * ייצוא PDF ב-wasmJs דרך window.print().
 * הדפדפן יפתח דיאלוג הדפסה שמאפשר שמירה כ-PDF.
 */
actual class PdfExporter {
    actual fun exportToPdf() {
        window.print()
    }
}
