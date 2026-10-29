package com.suginach.typesetter.platform

import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.serialization.json.Json

/**
 * אחסון הגדרות — expect/actual.
 * ב-wasmJs: localStorage.
 * ב-desktop: קובץ בתיקיית המשתמש.
 */
expect class SettingsStorage() {
    fun save(settings: TypesetterSettings)
    fun load(): TypesetterSettings?
}

/**
 * סריאליזציה של הגדרות ל-JSON — משותף לכל הפלטפורמות.
 */
object SettingsSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun toJson(settings: TypesetterSettings): String {
        return json.encodeToString(TypesetterSettings.serializer(), settings)
    }

    fun fromJson(jsonString: String): TypesetterSettings? {
        return try {
            json.decodeFromString(TypesetterSettings.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
