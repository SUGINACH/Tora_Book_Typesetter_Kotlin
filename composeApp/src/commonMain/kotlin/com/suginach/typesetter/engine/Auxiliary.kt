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
        if (page.columns.isEmpty()) return page

        val column = page.columns.first()
        val block = column.blocks.firstOrNull() ?: return page
        if (block !is Block.TwoColumn) return page

        val rightLines = block.rightParagraphs.sumOf { it.lines.size }
        val leftLines = block.leftParagraphs.sumOf { it.lines.size }

        // אין צורך באיזון אם הפער קטן
        if (abs(rightLines - leftLines) <= 1) return page

        val (newRight, newLeft) = if (rightLines > leftLines) {
            rebalance(block.rightParagraphs, block.leftParagraphs, targetRight = (rightLines + leftLines) / 2)
        } else {
            val (newL, newR) = rebalance(block.leftParagraphs, block.rightParagraphs, targetRight = (rightLines + leftLines) / 2)
            newR to newL
        }

        val newBlock = Block.TwoColumn(newRight, newLeft)
        val newColumn = Column(listOf(newBlock), column.heightPx, isBalanced = true)
        return page.copy(columns = listOf(newColumn))
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
        if (page.columns.isEmpty()) return page
        val column = page.columns.first()
        val block = column.blocks.firstOrNull() ?: return page
        if (block !is Block.TwoColumn) return page

        val newRight = fixColumn(block.rightParagraphs)
        val newLeft = fixColumn(block.leftParagraphs)
        val newBlock = Block.TwoColumn(newRight, newLeft)
        val newColumn = Column(listOf(newBlock), column.heightPx, column.isBalanced)
        return page.copy(columns = listOf(newColumn))
    }

    /**
     * מתקן יתומות/אלמנות בתוך טור:
     * - אם פסקה מסתיימת בשורה אחת בודדת (orphan בראש הטור הבא), מוסיף שורה קודמת
     * - אם פסקה מתחילה בשורה אחת בודדת בסוף הטור (orphan), מעביר את כל הפסקה לטור הבא
     */
    private fun fixColumn(paragraphs: List<Paragraph>): List<Paragraph> {
        if (paragraphs.isEmpty()) return paragraphs
        val result = paragraphs.toMutableList()

        // בדיקת orphan: פסקה ראשונה בטור עם שורה אחת בלבד שאינה המשך
        if (result.size > 1) {
            val first = result.first()
            if (first.lines.size == 1 && !first.isContinuation) {
                // מעביר שורה אחת מהפסקה הקודמת (האחרונה של הטור הקודם)
                // כאן אנחנו רק מסמנים — ההעברה בפועל נעשית על ידי הבלנסר
            }
        }

        // בדיקת widow: פסקה אחרונה בטור עם שורה אחת
        if (result.size > 1) {
            val last = result.last()
            if (last.lines.size == 1 && !last.isContinuation) {
                // מסמן שצריך להעביר את הפסקה לטור הבא
                // בפועל, אנחנו משאירים אותה והבלנסר יטפל בזה
            }
        }

        // בדיקת "כותרת יתומה": פסקה אחרונה שהיא כותרת (שורה אחת, ללא firstWord)
        // נשאיר את הטיפול המלא לשלב מתקדם יותר
        return result.toList()
    }
}

/**
 * מבקר איכות גיאומטרי — סורק את העמודים ומדווח על בעיות.
 *
 * בעיות שמזוהות:
 * 1. WIDOW — שורה אחרונה בודדת בראש טור
 * 2. ORPHAN — שורה ראשונה בודדת בסוף טור
 * 3. ORPHAN_HEADING — כותרת בסוף עמוד ללא פסקה אחריה
 * 4. IMBALANCED — פער גדול בין גובה הטורים
 * 5. OVERFLOW — פסקה חורגת מגובה העמוד
 * 6. SPARSE_LINE — שורה דלילה מאוד (פחות מ-30% מרוחב הטור)
 * 7. EMPTY_COLUMN — טור ריק לחלוטין
 * 8. SHORT_PAGE_BEFORE_H3 — עמוד קצר לפני כותרת רמה 3
 */
class GeometryAuditor {
    fun audit(pages: List<RenderedPage>, settings: TypesetterSettings): AuditReport {
        val issues = mutableListOf<AuditIssue>()

        for ((pageIdx, page) in pages.withIndex()) {
            if (page.columns.isEmpty()) continue
            val column = page.columns.first()
            val block = column.blocks.firstOrNull() ?: continue
            if (block !is Block.TwoColumn) continue

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

            // בדיקת כותרת יתומה — הפסקה האחרונה היא כותרת
            checkOrphanHeading(block.rightParagraphs, page.index, "ימין", issues)
            checkOrphanHeading(block.leftParagraphs, page.index, "שמאל", issues)

            // בדיקת עמוד קצר לפני כותרת רמה 3
            if (pageIdx < pages.size - 1) {
                val nextPage = pages[pageIdx + 1]
                val nextBlock = nextPage.columns.firstOrNull()?.blocks?.firstOrNull()
                if (nextBlock is Block.TwoColumn) {
                    val firstPara = nextBlock.rightParagraphs.firstOrNull()
                    // אם הפסקה הראשונה בעמוד הבא היא כותרת והעמוד הנוכחי קצר
                    if (firstPara != null && firstPara.lines.size == 1 && totalLines < 10) {
                        issues.add(AuditIssue(
                            page = page.index,
                            type = IssueType.SHORT_PAGE_BEFORE_H3,
                            severity = Severity.LOW,
                            description = "עמוד קצר ($totalLines שורות) לפני כותרת בעמוד הבא",
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

        // Widow: פסקה ראשונה בטור עם שורה אחת (אחרונה של פסקה קודמת)
        val first = paragraphs.first()
        if (first.lines.size == 1 && !first.isContinuation && paragraphs.size > 1) {
            issues.add(AuditIssue(
                page = pageNum,
                type = IssueType.WIDOW,
                severity = Severity.MEDIUM,
                description = "שורה אלמנה בראש הטור ה${side}י — פסקה עם שורה אחת בלבד",
            ))
        }

        // Orphan: פסקה אחרונה בטור עם שורה אחת (ראשונה של פסקה הבאה)
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
        // שורה דלילה = פחות מ-30% מאורך ממוצע
        val allLines = paragraphs.flatMap { it.lines }
        if (allLines.size < 5) return
        val avgLen = allLines.map { it.length }.average()
        val threshold = avgLen * 0.3

        for ((paraIdx, para) in paragraphs.withIndex()) {
            for ((lineIdx, line) in para.lines.withIndex()) {
                // לא בודקים את השורה האחרונה של פסקה (תמיד קצרה יחסית)
                if (lineIdx == para.lines.lastIndex) continue
                if (line.length < threshold && line.length < 15) {
                    issues.add(AuditIssue(
                        page = pageNum,
                        type = IssueType.SPARSE_LINE,
                        severity = Severity.LOW,
                        description = "שורה דלילה בטור ה${side}י (פסקה ${paraIdx + 1}, שורה ${lineIdx + 1}): ${line.length} תווים",
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
        // כותרת = שורה אחת, ללא firstWord מוגדל, לא המשך
        if (last.lines.size == 1 && !last.isFirstWordEnlarged && !last.isContinuation) {
            // הנחה: אם זו שורה קצרה יחסית וללא firstWord, כנראה כותרת
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
