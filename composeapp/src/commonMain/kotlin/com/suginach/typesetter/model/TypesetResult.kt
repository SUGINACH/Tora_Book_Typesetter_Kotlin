package com.suginach.typesetter.model

/**
 * תוצאת העימוד — רשימת עמודים + דוח ביקורת
 */
data class TypesetResult(
    val pages: List<RenderedPage>,
    val footnotes: Map<Int, List<FootnoteEntry>> = emptyMap(),
    val endnotes: Map<Int, List<EndnoteEntry>> = emptyMap(),
    val tableOfContents: TocEntry? = null,
    val auditReport: AuditReport = AuditReport(emptyList()),
    val warnings: List<TypesetWarning> = emptyList(),
)

/**
 * עמוד מרונדר — מחולק ל-3 חלקים אנכיים:
 * 1. headerRow (כותרות עליונות)
 * 2. bodyArea (אזור הטקסט המרכזי)
 * 3. footerRow (כותרת תחתונה — מספר עמוד)
 *
 * bodyArea מחולק אופקית ל-2 חלקים:
 * 1. mainArea (טקסט עיקרי + כותרות צד)
 * 2. footnoteArea (הערות שוליים)
 *
 * mainArea מחולק אופקית ל-3 חלקים:
 * 1. rightSideNotes (כותרות צד ימין)
 * 2. textArea (הטקסט עצמו)
 * 3. leftSideNotes (כותרות צד שמאל)
 */
data class RenderedPage(
    val index: Int,
    val pageNumber: String,
    val role: PageRole,
    val headerRow: HeaderRow? = null,
    val bodyArea: BodyArea = BodyArea(),
    val footerRow: FooterRow? = null,
    val pageNumberFooter: String? = null,
    val background: BackgroundSpec? = null,
    // שדות מורשים לצורך תאימות לאחור — יוסרו בעתיד
    val columns: List<Column> = emptyList(),
    val footnoteArea: FootnoteArea? = null,
    val sideNotes: List<SideNote> = emptyList(),
) {
    /** גישה נוחה לטורים (מסונכרן עם bodyArea) */
    val effectiveColumns: List<Column> get() = if (columns.isNotEmpty()) columns else bodyArea.mainArea.textArea.columns
    val effectiveFootnoteArea: FootnoteArea? get() = footnoteArea ?: bodyArea.footnoteArea
}

/**
 * אזור הטקסט המרכזי בעמוד.
 */
data class BodyArea(
    val mainArea: MainArea = MainArea(),
    val footnoteArea: FootnoteArea? = null,
)

/**
 * אזור טקסט עיקרי עם כותרות צד.
 */
data class MainArea(
    val rightSideNotes: List<SideNoteEntry> = emptyList(),
    val textArea: TextArea = TextArea(),
    val leftSideNotes: List<SideNoteEntry> = emptyList(),
)

/**
 * אזור הטקסט — מכיל רצף של בלוקים (טורים / כותרות חוצות / מחלקות).
 */
data class TextArea(
    val blocks: List<TextBlock> = emptyList(),
    val heightPx: Double = 0.0,
    val isBalanced: Boolean = false,
) {
    /** גישה נוחה לרשימת הטורים (לצורך תאימות) */
    val columns: List<Column> get() = blocks.mapNotNull { (it as? TextBlock.TwoColumn)?.let { col -> Column(listOf(), 0.0) } }
}

/**
 * בלוק טקסט — יחידת עימוד בסיסית בתוך אזור הטקסט.
 */
sealed class TextBlock {
    /** עימוד שני טורים — המצב הרגיל לטקסט רגיל */
    data class TwoColumn(
        val rightParagraphs: List<Paragraph>,
        val leftParagraphs: List<Paragraph>,
        val isBalanced: Boolean = false,
    ) : TextBlock()

    /** עימוד טור אחד — לכותרות 2/3 */
    data class SingleColumn(
        val paragraphs: List<Paragraph>,
        val heading: Heading? = null,
    ) : TextBlock()

    /** כותרת חוצה טורים (full-width) — לכותרות 2/3 */
    data class HeadingSpan(val heading: Heading) : TextBlock()

    /** מחלק עיטורי — לסוף קטע של כותרת 2 (לפני כותרת 2 הבאה או בסוף חלק) */
    data class Divider(val ornament: String, val isChapterEnd: Boolean = false) : TextBlock()
}

/** תאימות לאחור — ה-Block הישן */
typealias Block = TextBlock

/**
 * עמודה ישנה — לתאימות. המודל החדש משתמש ב-TextArea.
 */
data class Column(
    val blocks: List<Block>,
    val heightPx: Double,
    val isBalanced: Boolean = false,
)

enum class PageRole {
    REGULAR, H0_OPEN, H1_SHAAR, H2_OPEN, H3_SPAN,
    SHAAR_MAIN, SHAAR_BACK, SHAAR_SUB,
    TOC_GENERAL, TOC_DETAILED,
    ENDNOTES, CUSTOM
}

/**
 * פסקה עם כל תכונות העיצוב הנדרשות.
 */
data class Paragraph(
    val text: String,
    val lines: List<ParagraphLine>,
    val isFirstWordEnlarged: Boolean = false,
    val firstWord: String? = null,
    val firstWordStyle: FirstWordStyle? = null,
    val isContinuation: Boolean = false,
    val isEnd: Boolean = false,
    val customStyleId: String? = null,
    val footnoteMarkers: List<FootnoteMarker> = emptyList(),
    val alignment: TextAlignment = TextAlignment.JUSTIFIED,
    val lastLineAlignment: TextAlignment = TextAlignment.CENTER,
    /** עיצוב כותרת אם הפסקה היא כותרת 4/5/6 */
    val headingStyle: HeadingStyle? = null,
) {
    /** תאימות לאחור — מאפשר שימוש ב-lines כ-List<String> */
    constructor(
        text: String,
        lines: List<String>,
        isFirstWordEnlarged: Boolean = false,
        firstWord: String? = null,
        isContinuation: Boolean = false,
        isEnd: Boolean = false,
        customStyleId: String? = null,
        footnoteMarkers: List<FootnoteMarker> = emptyList(),
    ) : this(
        text = text,
        lines = lines.map { ParagraphLine(it) },
        isFirstWordEnlarged = isFirstWordEnlarged,
        firstWord = firstWord,
        isContinuation = isContinuation,
        isEnd = isEnd,
        customStyleId = customStyleId,
        footnoteMarkers = footnoteMarkers,
    )

    /** גישה נוחה לשורות כמחרוזות */
    val lineTexts: List<String> get() = lines.map { it.text }
}

/**
 * שורה בודדת בפסקה — כוללת מידע עיצוב.
 */
data class ParagraphLine(
    val text: String,
    val isFirstLine: Boolean = false,
    val isLastLine: Boolean = false,
    val hasFirstWord: Boolean = false,
    val alignment: TextAlignment = TextAlignment.JUSTIFIED,
)

/**
 * יישור שורה.
 */
enum class TextAlignment {
    LEFT, RIGHT, CENTER, JUSTIFIED
}

/**
 * סגנון המילה הראשונה בפסקה.
 */
data class FirstWordStyle(
    val fontFamily: String = "Frank-Ruehl",
    val fontSizePt: Double = 18.0,
    val isBold: Boolean = false,
    /** חלון תחת המילה הראשונה — רווח אופקי מתחת */
    val hasWindow: Boolean = true,
    val windowWidthChars: Int = 3,
)

/**
 * כותרת עם כל תכונות העיצוב.
 */
data class Heading(
    val level: Int,
    val text: String,
    val fontFamily: String = "Frank-Ruehl",
    val fontSizePt: Int = 14,
    val isBold: Boolean = true,
    val spaceBeforePt: Int = 0,
    val spaceAfterPt: Int = 0,
    val simanBadge: String? = null,
    val ornamentTop: String? = null,
    val ornamentBottom: String? = null,
    val ornamentRight: String? = null,
    val ornamentLeft: String? = null,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val customStyleId: String? = null,
)

/**
 * עיצוב כותרת אינליין (לרמות 4/5/6) — מוטמע בתוך Paragraph.
 */
data class HeadingStyle(
    val level: Int,
    val fontSizePt: Int,
    val isBold: Boolean,
    val spaceBeforePt: Int = 0,
    val spaceAfterPt: Int = 0,
    val alignment: TextAlignment = TextAlignment.RIGHT,
)

/**
 * שורת כותרת עליונה (3 חלקים: ימין / מרכז / שמאל).
 */
data class HeaderRow(
    val right: HeaderContent,
    val center: HeaderContent,
    val left: HeaderContent,
)

/**
 * שורת כותרת תחתונה — מספר עמוד.
 */
data class FooterRow(
    val pageNumber: String,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val frameStyle: String = "none",
)

sealed class HeaderContent {
    object Empty : HeaderContent()
    data class Text(val text: String) : HeaderContent()
    data class Ornament(val svgId: String) : HeaderContent()
}

/**
 * אזור הערות שוליים בתחתית האזור העיקרי.
 */
data class FootnoteArea(
    val header: String? = null,
    val ruleStyle: String = "plain",
    val columns: Int = 1,
    val entries: List<FootnoteEntry>,
    val heightPx: Double = 0.0,
)

data class FootnoteEntry(
    val marker: String,
    val text: String,
)

data class EndnoteEntry(
    val marker: String,
    val text: String,
)

/**
 * סמן הערת שוליים בטקסט — כתב עילי.
 */
data class FootnoteMarker(
    val marker: String,
    val positionInText: Int,
    /** הסגנון — superscript כברירת מחדל */
    val style: MarkerStyle = MarkerStyle.SUPERSCRIPT,
)

enum class MarkerStyle {
    SUPERSCRIPT, PARENTHESIZED, BRACKETED
}

/**
 * הערת צד — מופיעה בשוליים (ימין או שמאל).
 */
data class SideNote(
    val text: String,
    val anchorLineIndex: Int,
)

/**
 * רשומה בודדת של הערת צד עם מיקום.
 */
data class SideNoteEntry(
    val text: String,
    val anchorParagraphIdx: Int,
    val side: SideNoteSide = SideNoteSide.RIGHT,
)

enum class SideNoteSide { RIGHT, LEFT }

data class BackgroundSpec(
    val mode: BgMode,
    val color: String? = null,
    val imageUrl: String? = null,
    val opacity: Double = 1.0,
)

enum class BgMode { NONE, COLOR, TEXTURE, IMAGE }

data class TocEntry(
    val title: String,
    val pageNumber: String,
    val level: Int,
    val children: List<TocEntry> = emptyList(),
)

data class AuditReport(
    val issues: List<AuditIssue>,
)

data class AuditIssue(
    val page: Int,
    val type: IssueType,
    val severity: Severity,
    val description: String,
)

enum class IssueType {
    WIDOW, ORPHAN, ORPHAN_HEADING, IMBALANCED, OVERFLOW,
    TOFU, OVERLAP, SPARSE_LINE, EMPTY_COLUMN, FOOTNOTE_MARKER,
    FONT_FALLBACK, BROKEN_BRACKETS, TEXT_OUTSIDE_RANGE,
    SHORT_PAGE_BEFORE_H3, U_E000
}

enum class Severity { CRITICAL, MEDIUM, LOW }

data class TypesetWarning(
    val message: String,
    val lineIndex: Int? = null,
)
