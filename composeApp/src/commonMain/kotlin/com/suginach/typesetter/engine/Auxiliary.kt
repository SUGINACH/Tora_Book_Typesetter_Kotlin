package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.settings.TypesetterSettings
import kotlin.math.abs

/**
 * מאזן טורים בעמוד — מעביר פסקאות/שורות בין הטור הימני לשמאלי כדי להשוות גובה.
 *
 * אסטרטגיה:
 * 1. חישוב מספר השורות בכל טור
 * 2. אם הפער גדול מ-1 שורה, מעבירים פסקה שלמה מהטור הארוך לקצר
 * 3. אם אי אפשר להעביר פסקה שלמה, מעבירים שורות מהפסקה האחרונה
 */
class ColumnBalancer {
    fun balance(page: RenderedPage, settings: TypesetterSettings): RenderedPage {
        val blocks = page.bodyArea.mainArea.textArea.blocks
        if (blocks.isEmpty()) return page

        // אוסף את כל ה-TwoColumn blocks ומאזן אותם
        val newBlocks = blocks.map { block ->
            when (block) {
                is TextBlock.TwoColumn -> balanceTwoColumn(block)
                else -> block
            }
        }

        val newTextArea = page.bodyArea.mainArea.textArea.copy(blocks = newBlocks, isBalanced = true)
        val newMainArea = page.bodyArea.mainArea.copy(textArea = newTextArea)
        val newBodyArea = page.bodyArea.copy(mainArea = newMainArea)
        return page.copy(bodyArea = newBodyArea)
    }

    private fun balanceTwoColumn(block: TextBlock.TwoColumn): TextBlock.TwoColumn {
        val rightLines = block.rightParagraphs.sumOf { it.lines.size }
        val leftLines = block.leftParagraphs.sumOf { it.lines.size }

        if (abs(rightLines - leftLines) <= 1) return block

        val (newRight, newLeft) = if (rightLines > leftLines) {
            rebalance(block.rightParagraphs, block.leftParagraphs, targetRight = (rightLines + leftLines) / 2)
        } else {
            val (newL, newR) = rebalance(block.leftParagraphs, block.rightParagraphs, targetRight = (rightLines + leftLines) / 2)
            newR to newL
        }

        return block.copy(rightParagraphs = newRight, leftParagraphs = newLeft, isBalanced = true)
    }

    /**
     * מעביר פסקאות מ-[from] ל-[to] עד ש-[from] מגיע ל-[targetRight] שורות.
     */
    private fun rebalance(
        from: List<Paragraph>,
        to: List<Paragraph>,
        targetRight: Int,
    ): Pair<List<Paragraph>, List<Paragraph>> {
        var currentFrom = from.toMutableList()
        var currentTo = to.toMutableList()
        var fromLines = currentFrom.sumOf { it.lines.size }

        // נסה להעביר פסקאות שלמות מהסוף
        while (fromLines > targetRight + 1 && currentFrom.size > 1) {
            val last = currentFrom.removeLast()
            currentTo.add(0, last)
            fromLines -= last.lines.size
        }

        // אם עדיין יש פער גדול, נסה לפצל את הפסקה האחרונה
        if (fromLines > targetRight + 2 && currentFrom.isNotEmpty()) {
            val last = currentFrom.removeLast()
            val splitPoint = (last.lines.size / 2).coerceAtLeast(1)
            val keepLines = last.lines.subList(0, splitPoint)
            val moveLines = last.lines.subList(splitPoint, last.lines.size)

            if (keepLines.isNotEmpty()) {
                currentFrom.add(last.copy(lines = keepLines, isEnd = false))
            }
            if (moveLines.isNotEmpty()) {
                currentTo.add(0, last.copy(
                    lines = moveLines,
                    isFirstWordEnlarged = false,
                    firstWord = null,
                    firstWordStyle = null,
                    isContinuation = true,
                ))
            }
        }

        return currentFrom.toList() to currentTo.toList()
    }
}

/**
 * שומר על כללי מניעת יתומות/אלמנות:
 * - אלמנה (widow): השורה האחרונה של פסקה שנשארה לבד בראש הטור/עמוד הבא
 * - יתומה (orphan): השורה הראשונה של פסקה שנשארה לבד בסוף הטור/עמוד הקודם
 * - כותרת יתומה: כותרת בסוף עמוד ללא פסקה אחריה
 */
class WidowOrphanGuard {
    fun guard(page: RenderedPage, settings: TypesetterSettings): RenderedPage {
        val blocks = page.bodyArea.mainArea.textArea.blocks
        if (blocks.isEmpty()) return page

        val newBlocks = blocks.map { block ->
            when (block) {
                is TextBlock.TwoColumn -> block.copy(
                    rightParagraphs = fixColumn(block.rightParagraphs),
                    leftParagraphs = fixColumn(block.leftParagraphs),
                )
                else -> block
            }
        }

        val newTextArea = page.bodyArea.mainArea.textArea.copy(blocks = newBlocks)
        val newMainArea = page.bodyArea.mainArea.copy(textArea = newTextArea)
        val newBodyArea = page.bodyArea.copy(mainArea = newMainArea)
        return page.copy(bodyArea = newBodyArea)
    }

    private fun fixColumn(paragraphs: List<Paragraph>): List<Paragraph> {
        // TODO: מימוש מלא — כרגע רק מחזיר את הרשימה כמו שהיא
        return paragraphs
    }
}

/**
 * מבקר איכות גיאומטרי — סורק את העמודים ומדווח על בעיות.
 */
class GeometryAuditor {
    fun audit(pages: List<RenderedPage>, settings: TypesetterSettings): AuditReport {
        val issues = mutableListOf<AuditIssue>()

        for ((pageIdx, page) in pages.withIndex()) {
            val blocks = page.bodyArea.mainArea.textArea.blocks

            for (block in blocks) {
                if (block !is TextBlock.TwoColumn) continue

                // בדיקת איזון טורים
                val rightLines = block.rightParagraphs.sumOf { it.lines.size }
                val leftLines = block.leftParagraphs.sumOf { it.lines.size }
                val totalLines = rightLines + leftLines
                if (totalLines > 0) {
                    val diff = abs(rightLines - leftLines)
                    if (diff >= 3 && minOf(rightLines, leftLines) > 0) {
                        issues.add(AuditIssue(
                            page = page.index,
                            type = IssueType.IMBALANCED,
                            severity = Severity.MEDIUM,
                            description = "פער של $diff שורות בין הטורים (ימין=$rightLines, שמאל=$leftLines)",
                        ))
                    }
                }

                // בדיקת טור ריק
                if (rightLines == 0 && leftLines > 0) {
                    issues.add(AuditIssue(
                        page = page.index,
                        type = IssueType.EMPTY_COLUMN,
                        severity = Severity.LOW,
                        description = "הטור הימני ריק",
                    ))
                }
                if (leftLines == 0 && rightLines > 0) {
                    issues.add(AuditIssue(
                        page = page.index,
                        type = IssueType.EMPTY_COLUMN,
                        severity = Severity.LOW,
                        description = "הטור השמאלי ריק",
                    ))
                }

                // בדיקת widow/orphan
                checkWidowOrphan(block.rightParagraphs, page.index, "ימין", issues)
                checkWidowOrphan(block.leftParagraphs, page.index, "שמאל", issues)

                // בדיקת שורות דלילות
                checkSparseLines(block.rightParagraphs, page.index, "ימין", issues)
                checkSparseLines(block.leftParagraphs, page.index, "שמאל", issues)

                // בדיקת כותרת יתומה
                checkOrphanHeading(block.rightParagraphs, page.index, "ימין", issues)
                checkOrphanHeading(block.leftParagraphs, page.index, "שמאל", issues)
            }

            // בדיקת עמוד קצר לפני כותרת רמה 3
            if (pageIdx < pages.size - 1) {
                val nextPage = pages[pageIdx + 1]
                val nextBlocks = nextPage.bodyArea.mainArea.textArea.blocks
                val firstBlock = nextBlocks.firstOrNull()
                if (firstBlock is TextBlock.HeadingSpan && firstBlock.heading.level == 3) {
                    val totalLines = blocks.sumOf {
                        when (it) {
                            is TextBlock.TwoColumn -> it.rightParagraphs.sumOf { p -> p.lines.size } + it.leftParagraphs.sumOf { p -> p.lines.size }
                            is TextBlock.SingleColumn -> it.paragraphs.sumOf { p -> p.lines.size }
                            else -> 0
                        }
                    }
                    if (totalLines < 10) {
                        issues.add(AuditIssue(
                            page = page.index,
                            type = IssueType.SHORT_PAGE_BEFORE_H3,
                            severity = Severity.LOW,
                            description = "עמוד קצר ($totalLines שורות) לפני כותרת רמה 3 בעמוד הבא",
                        ))
                    }
                }
            }
        }

        return AuditReport(issues)
    }

    private fun checkWidowOrphan(
        paragraphs: List<Paragraph>,
        pageNum: Int,
        side: String,
        issues: MutableList<AuditIssue>,
    ) {
        if (paragraphs.isEmpty()) return

        val first = paragraphs.first()
        if (first.lines.size == 1 && !first.isContinuation && paragraphs.size > 1) {
            issues.add(AuditIssue(
                page = pageNum,
                type = IssueType.WIDOW,
                severity = Severity.MEDIUM,
                description = "שורה אלמנה בראש הטור ה${side}י — פסקה עם שורה אחת בלבד",
            ))
        }

        val last = paragraphs.last()
        if (last.lines.size == 1 && paragraphs.size > 1) {
            issues.add(AuditIssue(
                page = pageNum,
                type = IssueType.ORPHAN,
                severity = Severity.MEDIUM,
                description = "שורה יתומה בסוף הטור ה${side}י — פסקה עם שורה אחת בלבד",
            ))
        }
    }

    private fun checkSparseLines(
        paragraphs: List<Paragraph>,
        pageNum: Int,
        side: String,
        issues: MutableList<AuditIssue>,
    ) {
        val allLines = paragraphs.flatMap { it.lines }
        if (allLines.size < 5) return
        val avgLen = allLines.map { it.text.length }.average()
        val threshold = avgLen * 0.3

        for ((paraIdx, para) in paragraphs.withIndex()) {
            for ((lineIdx, line) in para.lines.withIndex()) {
                if (lineIdx == para.lines.lastIndex) continue
                if (line.text.length < threshold && line.text.length < 15) {
                    issues.add(AuditIssue(
                        page = pageNum,
                        type = IssueType.SPARSE_LINE,
                        severity = Severity.LOW,
                        description = "שורה דלילה בטור ה${side}י (פסקה ${paraIdx + 1}, שורה ${lineIdx + 1}): ${line.text.length} תווים",
                    ))
                }
            }
        }
    }

    private fun checkOrphanHeading(
        paragraphs: List<Paragraph>,
        pageNum: Int,
        side: String,
        issues: MutableList<AuditIssue>,
    ) {
        if (paragraphs.isEmpty()) return
        val last = paragraphs.last()
        if (last.lines.size == 1 && !last.isFirstWordEnlarged && !last.isContinuation) {
            if (last.text.length < 80) {
                issues.add(AuditIssue(
                    page = pageNum,
                    type = IssueType.ORPHAN_HEADING,
                    severity = Severity.MEDIUM,
                    description = "כותרת יתומה בסוף הטור ה${side}י — עלולה להישאר ללא תוכן אחריה",
                ))
            }
        }
    }
}
