package com.suginach.typesetter.platform

/**
 * פלטפורמת ייצוא — expect/actual.
 * ב-wasmJs: window.print() להדפסה/PDF.
 * ב-desktop: ייצוא דרך AWT Printing.
 */
expect class PdfExporter() {
    /**
     * מייצא את התוכן ל-PDF או פותח דיאלוג הדפסה.
     */
    fun exportToPdf()
}
