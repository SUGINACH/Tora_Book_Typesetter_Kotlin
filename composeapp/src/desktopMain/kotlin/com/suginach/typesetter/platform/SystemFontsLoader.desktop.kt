package com.suginach.typesetter.platform

import java.awt.GraphicsEnvironment

/**
 * גופני מערכת ב-desktop דרך AWT GraphicsEnvironment.
 */
actual class SystemFontsLoader {
    actual fun loadAvailableFonts(): List<String> {
        return try {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames.toList()
        } catch (e: Exception) {
            listOf("Frank-Ruehl", "Arial", "Times New Roman", "David", "Tahoma")
        }
    }
}
