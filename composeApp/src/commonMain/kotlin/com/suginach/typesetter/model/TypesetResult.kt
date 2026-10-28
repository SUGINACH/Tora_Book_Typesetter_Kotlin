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

data class RenderedPage(
    val index: Int,
    val pageNumber: String,
    val role: PageRole,
    val headerRow: HeaderRow? = null,
    val columns: List<Column> = emptyList(),
    val footnoteArea: FootnoteArea? = null,
    val pageNumberFooter: String? = null,
    val background: BackgroundSpec? = null,
    val sideNotes: List<SideNote> = emptyList(),
)

enum class PageRole {
    REGULAR, H0_OPEN, H1_SHAAR, H2_OPEN, H3_SPAN,
    SHAAR_MAIN, SHAAR_BACK, SHAAR_SUB,
    TOC_GENERAL, TOC_DETAILED,
    ENDNOTES, CUSTOM
}

data class Column(
    val blocks: List<Block>,
    val heightPx: Double,
    val isBalanced: Boolean = false,
)

sealed class Block {
    data class TwoColumn(val rightParagraphs: List<Paragraph>, val leftParagraphs: List<Paragraph>) : Block()
    data class SingleColumn(val paragraphs: List<Paragraph>) : Block()
    data class HeadingSpan(val heading: Heading) : Block()
    data class HeadingInline(val heading: Heading) : Block()
    data class Divider(val ornament: String) : Block()
}

data class Paragraph(
    val text: String,
    val lines: List<String>,
    val isFirstWordEnlarged: Boolean = false,
    val firstWord: String? = null,
    val isContinuation: Boolean = false,
    val isEnd: Boolean = false,
    val customStyleId: String? = null,
    val footnoteMarkers: List<FootnoteMarker> = emptyList(),
)

data class Heading(
    val level: Int,
    val text: String,
    val simanBadge: String? = null,
    val ornamentTop: String? = null,
    val ornamentBottom: String? = null,
    val ornamentRight: String? = null,
    val ornamentLeft: String? = null,
    val customStyleId: String? = null,
)

data class HeaderRow(
    val right: HeaderContent,
    val center: HeaderContent,
    val left: HeaderContent,
)

sealed class HeaderContent {
    object Empty : HeaderContent()
    data class Text(val text: String) : HeaderContent()
    data class Ornament(val svgId: String) : HeaderContent()
}

data class FootnoteArea(
    val header: String? = null,
    val ruleStyle: String = "plain",
    val columns: Int = 1,
    val entries: List<FootnoteEntry>,
)

data class FootnoteEntry(
    val marker: String,
    val text: String,
)

data class EndnoteEntry(
    val marker: String,
    val text: String,
)

data class FootnoteMarker(
    val marker: String,
    val positionInText: Int,
)

data class SideNote(
    val text: String,
    val anchorLineIndex: Int,
)

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
