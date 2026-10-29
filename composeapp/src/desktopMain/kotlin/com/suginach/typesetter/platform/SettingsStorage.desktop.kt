package com.suginach.typesetter.platform

import com.suginach.typesetter.settings.TypesetterSettings
import java.io.File

/**
 * אחסון הגדרות ב-desktop דרך קובץ בתיקיית המשתמש.
 */
actual class SettingsStorage {
    private val settingsFile = File(System.getProperty("user.home"), ".tora_typesetter.json")

    actual fun save(settings: TypesetterSettings) {
        try {
            val json = SettingsSerializer.toJson(settings)
            settingsFile.writeText(json, charset("UTF-8"))
        } catch (e: Exception) {
            // שגיאת כתיבה
        }
    }

    actual fun load(): TypesetterSettings? {
        return try {
            if (!settingsFile.exists()) return null
            val json = settingsFile.readText(charset("UTF-8"))
            SettingsSerializer.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
