package com.suginach.typesetter.platform

/**
 * פלטפורמת ייצוא טקסט — expect/actual.
 * ב-wasmJs: הורדת קובץ דרך Blob.
 * ב-desktop: JFileChooser לשמירה.
 */
expect class TextExporter() {
    /**
     * שומר טקסט לקובץ.
     * @param text הטקסט לשמירה
     * @param suggestedName שם קובץ מוצע
     */
    fun exportText(text: String, suggestedName: String = "typeset.txt")
}
