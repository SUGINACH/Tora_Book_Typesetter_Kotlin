package com.suginach.typesetter.platform

/**
 * גופני מערכת ב-wasmJs.
 * מחזיר רשימת גופנים מובנית כי queryLocalFonts() דורש הרשאות מיוחדות.
 */
actual class SystemFontsLoader {
    actual fun loadAvailableFonts(): List<String> {
        return listOf(
            "Frank-Ruehl",
            "Assistant",
            "Arial",
            "Times New Roman",
            "David",
            "Tahoma",
            "Noto Sans Hebrew",
            "Noto Serif Hebrew",
            "Rubik",
            "Heebo",
        )
    }
}
