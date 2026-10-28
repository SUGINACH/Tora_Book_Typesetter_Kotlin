package com.suginach.typesetter.model

/**
 * טוקנים — תוצאת פרסור הטקסט הגולמי
 */
sealed class Token {
    abstract val lineIndex: Int

    data class HeadingToken(
        override val lineIndex: Int,
        val level: Int,
        val text: String,
        val customStyleId: String? = null,
    ) : Token()

    data class ParagraphToken(
        override val lineIndex: Int,
        val rawText: String,
        val inlines: List<InlineSegment>,
        val sideNote: String? = null,
        val endNote: String? = null,
        val customStyleId: String? = null,
    ) : Token()
}

sealed class InlineSegment {
    data class Text(val content: String) : InlineSegment()
    data class Footnote(val content: String, val marker: String) : InlineSegment()
}
