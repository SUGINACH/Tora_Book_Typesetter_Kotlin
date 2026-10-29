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
    val layoutMode: String = "inline", // מזהה פריסה כ-String לסריאליזציה גמישה
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

/**
 * מוקש 2 באפיון: LayoutMode כ-sealed class במקום enum class
 * כדי לאפשר הרחבת פריסות דינמית ללא קומפילציה מחדש.
 *
 * המשתמש יכול להוסיף פריסות חדשות דרך Custom LayoutMode
 * עם id ייחודי ופרמטרים מותאמים אישית.
 */
sealed class LayoutMode {
    /** בתוך הטור הרגיל */
    data object Inline : LayoutMode()
    /** חוצה טורים (כמו כותרת 3) */
    data object SingleColumnWide : LayoutMode()
    /** בשוליים (כמו כותרת 7) */
    data object SideNote : LayoutMode()
    /** בתחתית העמוד */
    data object Footnote : LayoutMode()
    /** בסוף החלק */
    data object Endnote : LayoutMode()
    /** יצירת מיקום חדש */
    data object NewLocation : LayoutMode()
    /** פריסה מותאמת אישית — מאפשרת הרחבה דינמית */
    data class Custom(val id: String, val params: Map<String, String> = emptyMap()) : LayoutMode()

    companion object {
        /** רישום דינמי של פריסות מותאמות אישית */
        private val customRegistry = mutableMapOf<String, LayoutMode>()

        fun register(id: String, mode: LayoutMode) {
            customRegistry[id] = mode
        }

        fun getCustom(id: String): LayoutMode? = customRegistry[id]

        /** ברירת מחדל — Inline */
        val DEFAULT: LayoutMode = Inline
    }
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
