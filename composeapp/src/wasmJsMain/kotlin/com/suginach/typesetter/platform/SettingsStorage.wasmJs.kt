package com.suginach.typesetter.platform

import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.browser.window

/**
 * אחסון הגדרות ב-wasmJs דרך localStorage.
 */
actual class SettingsStorage {
    private val key = "tora_typesetter_settings"

    actual fun save(settings: TypesetterSettings) {
        val json = SettingsSerializer.toJson(settings)
        try {
            window.localStorage.setItem(key, json)
        } catch (e: Exception) {
            // localStorage עלול להיכשל במצב פרטי
        }
    }

    actual fun load(): TypesetterSettings? {
        return try {
            val json = window.localStorage.getItem(key) ?: return null
            SettingsSerializer.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
