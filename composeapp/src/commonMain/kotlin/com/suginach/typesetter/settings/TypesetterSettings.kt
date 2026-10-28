package com.suginach.typesetter.settings

import kotlinx.serialization.Serializable

@Serializable
data class TypesetterSettings(
    // ערכות נושא ושערים
    val activeTheme: String = "builtin_1",
    val showMainShaar: Boolean = true,
    val showBackShaar: Boolean = true,
    val showSubShaar: Boolean = true,
    val enableShaarBoxEditor: Boolean = false,

    // הגדרות הערות
    val footnoteDelimOpen: String = "[",
    val footnoteDelimClose: String = "]",
    val endnotePrefix: String = "הערת סיום -",
    val noteNumberingStyle: String = "gematria",
    val noteMarkerType: String = "superscript",
    val noteMarkerSize: Int = 8,
    val noteListingNumSize: Int = 8,
    val noteNumSeparator: String = ".",
    val noteRuleStyle: String = "plain",
    val footnoteColumns: Int = 1,
    val footnoteResetMode: String = "continuous",
    val footnoteHeader: String = "",

    // איכות עימוד
    val avoidWidowsOrphans: Boolean = true,
    val balanceChapterEnd: Boolean = true,

    // כותרות (0-7) — מצב עימוד לכל רמה
    val headingModes: Map<Int, String> = mapOf(
        0 to "new_page_1col",
        1 to "sub_shaar",
        2 to "new_page_1col",
        3 to "span_1col",
        4 to "inline_2col",
        5 to "inline_2col",
        6 to "inline_2col",
        7 to "side_note",
    ),
    val headingFonts: Map<Int, String> = mapOf(
        0 to "Frank-Ruehl", 1 to "Frank-Ruehl", 2 to "Frank-Ruehl",
        3 to "Frank-Ruehl", 4 to "Frank-Ruehl", 5 to "Frank-Ruehl",
        6 to "Frank-Ruehl", 7 to "Frank-Ruehl",
    ),
    val headingSizes: Map<Int, Int> = mapOf(
        0 to 24, 1 to 20, 2 to 16, 3 to 14, 4 to 12, 5 to 12, 6 to 12, 7 to 10,
    ),
    /** ריווח לפני כותרת בכל רמה (pt) */
    val headingSpaceBefore: Map<Int, Int> = mapOf(
        0 to 24, 1 to 18, 2 to 14, 3 to 10, 4 to 6, 5 to 4, 6 to 4, 7 to 2,
    ),
    /** ריווח אחרי כותרת בכל רמה (pt) */
    val headingSpaceAfter: Map<Int, Int> = mapOf(
        0 to 18, 1 to 14, 2 to 10, 3 to 8, 4 to 4, 5 to 4, 6 to 2, 7 to 2,
    ),
    val headingBold: Map<Int, Boolean> = mapOf(
        0 to true, 1 to true, 2 to true, 3 to true,
        4 to true, 5 to true, 6 to true, 7 to false,
    ),
    val headingAlignment: Map<Int, String> = mapOf(
        0 to "center", 1 to "center", 2 to "center", 3 to "center",
        4 to "right", 5 to "right", 6 to "right", 7 to "right",
    ),
    val h2AutoSiman: Boolean = true,
    val h2SimanResetPerPart: Boolean = true,
    val h2OrnamentTop: String = "none",
    val h2OrnamentBottom: String = "none",
    val h3OrnamentRight: String = "none",
    val h3OrnamentLeft: String = "none",
    /** עיטורי כותרות — מזהי עיטורים זמינים */
    val ornamentStyle: String = "default",

    // גוף הטקסט
    val bodyFont: String = "Frank-Ruehl",
    val bodySize: Int = 12,
    val bodyLineHeight: Double = 1.34,
    val bodyAlignment: String = "justified",
    val bodyLastLineAlignment: String = "center",
    val firstWordFont: String = "Frank-Ruehl",
    val firstWordSize: Double = 1.5,
    val firstWordMode: String = "window",
    val firstWordCount: Int = 1,
    val firstWordMinLines: Int = 2,
    val firstWordBold: Boolean = false,
    val firstWordHasWindow: Boolean = true,
    val firstWordWindowChars: Int = 3,
    val dibburHamatchilEnabled: Boolean = false,

    // עיטורי סוף פרק — בסיום תוכן של כותרת 2, לפני כותרת 2 הבאה
    val chapterEndOrnament: String = "stars",
    val chapterEndOrnamentCount: Int = 3,

    // כותרות עליונות
    val headerRightType: String = "h1",
    val headerCenterType: String = "book_title",
    val headerLeftType: String = "empty",
    val headerAlternating: Boolean = false,
    val headerTitleSplit: Boolean = false,

    // תוכן עניינים
    val enableGeneralToc: Boolean = true,
    val enableDetailedToc: Boolean = true,

    // גריד וטיפוגרפיה
    val propCol: Double = 40.0,
    val propMargin: Double = 7.5,
    val propGutter: Double = 2.0,
    val propGap: Double = 2.0,
    val propMt: Int = 20,
    val propMb: Int = 20,
    val propMtH2: Int = 60,
    val gutterMargin: Boolean = false,
    val columnDivider: String = "none",
    val pageSizeWidth: Double = 210.0,
    val pageSizeHeight: Double = 297.0,
    val pageNumFormat: String = "gematria",
    val pageNumFrame: String = "none",
    val pageNumPosition: String = "bottom_center",
    val pageNumStartIdx: Int = 1,

    // רקעים
    val regularBgMode: String = "none",
    val h0OpenBgMode: String = "none",
    val h2OpenBgMode: String = "none",
) {
    companion object {
        fun default() = TypesetterSettings()
    }
}
