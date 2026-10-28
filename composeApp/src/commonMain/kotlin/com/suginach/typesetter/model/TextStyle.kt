package com.suginach.typesetter.model

import kotlinx.serialization.Serializable

/**
 * סוג טקסט מותאם אישית — הגדרה הניתנת לייבוא מ-JSON.
 * המשתמש יכול להגדיר סוגי טקסט חדשים עם כל תוחם.
 */
@Serializable
data class TextStyleDefinition(
    val id: String,
    val name: String,
    val trigger: TextTrigger,
    val style: TextStyleProperties = TextStyleProperties(),
    val layoutMode: LayoutMode = LayoutMode.INLINE,
    val noteConfig: NoteConfig? = null,
    val resetAt: String? = null,
)

@Serializable
sealed class TextTrigger {
    @Serializable
    data class LinePrefix(val prefix: String) : TextTrigger()

    @Serializable
    data class InlineMarker(val open: String, val close: String) : TextTrigger()
}

@Serializable
data class TextStyleProperties(
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String = "normal",
    val fontStyle: String = "normal",
    val textDecoration: String = "none",
    val color: String? = null,
    val backgroundColor: String? = null,
    val textAlign: String = "justify",
    val lineHeight: Double? = null,
    val letterSpacing: Double? = null,
    val textIndent: Double? = null,
    val marginTop: Double = 0.0,
    val marginBottom: Double = 0.0,
    val marginRight: Double = 0.0,
    val marginLeft: Double = 0.0,
)

enum class LayoutMode {
    INLINE,              // בתוך הטור הרגיל
    SINGLE_COLUMN_WIDE,  // חוצה טורים (כמו כותרת 3)
    SIDE_NOTE,           // בשוליים (כמו כותרת 7)
    FOOTNOTE,            // בתחתית העמוד
    ENDNOTE,             // בסוף החלק
    NEW_LOCATION,        // יצירת מיקום חדש
}

@Serializable
data class NoteConfig(
    val target: String = "footnote",  // "footnote" or "endnote"
    val numberingStyle: String = "gematria",
    val resetMode: String = "continuous",
)

@Serializable
data class PageTypeDefinition(
    val id: String,
    val name: String,
    val anchor: PageAnchor = PageAnchor.AFTER_ALL,
    val background: SerializableBackgroundSpec? = null,
    val textBoxes: List<TextBoxSpec> = emptyList(),
    val images: List<ImageSpec> = emptyList(),
    val showPageNumber: Boolean = false,
)

@Serializable
data class SerializableBackgroundSpec(
    val mode: String = "none",
    val color: String? = null,
    val imageUrl: String? = null,
    val opacity: Double = 1.0,
)

enum class PageAnchor {
    BEFORE_ALL, AFTER_MAIN_SHAAR, AFTER_GENERAL_TOC,
    BEFORE_EACH_PART, AFTER_EACH_PART, END_OF_EACH_PART, AFTER_ALL
}

@Serializable
data class TextBoxSpec(
    val text: String,
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double,
    val fontFamily: String = "Frank-Ruehl",
    val fontSize: Int = 12,
    val align: String = "center",
)

@Serializable
data class ImageSpec(
    val url: String,
    val xMm: Double,
    val yMm: Double,
    val widthMm: Double = 0.0,
    val heightMm: Double = 0.0,
)
