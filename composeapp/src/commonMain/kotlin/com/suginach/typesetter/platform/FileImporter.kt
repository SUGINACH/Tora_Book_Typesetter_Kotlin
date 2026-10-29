package com.suginach.typesetter.platform

/**
 * פלטפורמת ייבוא קובץ — expect/actual.
 * ב-wasmJs: HTML file input דרך JS interop.
 * ב-desktop: JFileChooser.
 */
expect class FileImporter() {
    /**
     * פותח דיאלוג בחירת קובץ וקורא את תוכנו כטקסט.
     * @param onResult נקרא עם תוכן הקובץ (או null אם בוטל)
     */
    fun importTextFile(onResult: (String?) -> Unit)
}
