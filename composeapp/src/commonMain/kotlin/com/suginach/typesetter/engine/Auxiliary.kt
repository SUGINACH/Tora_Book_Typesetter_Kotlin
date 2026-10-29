package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.settings.TypesetterSettings
import kotlin.math.abs

/**
 * מאזן טורים בעמוד באמצעות גישת Candidate-Based (ללא לולאות feedback).
 *
 * חוק ברזל (מוקש 2 חלק 5): איסור על העברת שורות איטרטיבית בלולאה
 * בין טורים, כדי למנוע לולאות oscillation. המנוע מייצר מראש מספר
 * קבוע של מועמדי חיתוך וירטואליים, מחשב Penalty Score לכל אחד,
 * ובוחר את המועמד עם הציון האופטימלי בצורה דטרמיניסטית.
 *
 * אלגוריתם:
 * 1. איחוד כל השורות משני הטורים לרצף אחד
 * 2. יצירת מועמדי חיתוך: חיתוך בכל מיקום אפשרי מ-N-3 עד N+3 (7 מועמדים)
 * 3. חישוב Penalty = |rightLines - leftLines| לכל מועמד
 * 4. בחירת המועמד עם Penalty מינימלי, עם tie-breaker דטרמיניסטי
 */
class ColumnBalancer {
    fun balance(page: RenderedPage, settings: TypesetterSettings): RenderedPage {
        val blocks = page.bodyArea.mainArea.textArea.blocks
        if (blocks.isEmpty()) return page

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

    /**
     * איזון טור אחד (TwoColumn) באמצעות Candidate-Based balance.
     */
    private fun balanceTwoColumn(block: TextBlock.TwoColumn): TextBlock.TwoColumn {
        val rightLines = block.rightParagraphs.sumOf { it.lines.size }
        val leftLines = block.leftParagraphs.sumOf { it.lines.size }

        // אין צורך באיזון אם הפער קטן
        if (abs(rightLines - leftLines) <= 1) return block

        // איחוד כל הפסקאות לרצף אחד — ראשית ימין, אחר כך שמאל
        val allParagraphs = block.rightParagraphs + block.leftParagraphs
        val totalLines = allParagraphs.sumOf { it.lines.size }
        if (totalLines == 0) return block

        // נקודת החיתוך האידיאלית = חצי מהשורות
        val idealSplit = totalLines / 2

        // יצירת 7 מועמדי חיתוך סביב הנקודה האידיאלית
        val candidates = (idealSplit - 3..idealSplit + 3).filter { it > 0 && it < totalLines }

        // חישוב Penalty לכל מועמד ובחירת המינימלי
        // Tie-breaker: מועמד עם splitPoint נמוך יותר (יותר שורות בימין)
        val bestCandidate = candidates.minByOrNull { splitPoint ->
            val rightLinesAtSplit = splitPoint
            val leftLinesAtSplit = totalLines - splitPoint
            abs(rightLinesAtSplit - leftLinesAtSplit)
        } ?: idealSplit

        // חלוקת הפסקאות לפי נקודת החיתוך הנבחרת
        val (newRight, newLeft) = splitParagraphsAtLine(allParagraphs, bestCandidate)

        return block.copy(rightParagraphs = newRight, leftParagraphs = newLeft, isBalanced = true)
    }

    /**
     * חלוקת רשימת פסקאות לשני טורים כך שהטור הימני יכיל בדיוק targetRightLines שורות.
     * אם פסקה חוצה את נקודת החיתוך, היא מתפצלת לשני חלקים.
     */
    private fun splitParagraphsAtLine(
        paragraphs: List<Paragraph>,
        targetRightLines: Int,
    ): Pair<List<Paragraph>, List<Paragraph>> {
        val rightParas = mutableListOf<Paragraph>()
        val leftParas = mutableListOf<Paragraph>()
        var linesUsed = 0

        for (para in paragraphs) {
            val paraLines = para.lines.size
            if (linesUsed + paraLines <= targetRightLines) {
                // כל הפסקה נכנסת בימין
                rightParas.add(para)
                linesUsed += paraLines
            } else if (linesUsed >= targetRightLines) {
                // כל הפסקה עוברת לשמאל
                leftParas.add(if (leftParas.isEmpty()) {
                    // הפסקה הראשונה בשמאל — מסמן כהמשך אם יש firstWord
                    if (para.isFirstWordEnlarged) {
                        para.copy(isFirstWordEnlarged = false, firstWord = null, firstWordStyle = null, isContinuation = true)
                    } else para
                } else para)
            } else {
                // הפסקה חוצה את נקודת החיתוך — פיצול
                val linesToKeep = targetRightLines - linesUsed
                if (linesToKeep > 0) {
                    rightParas.add(para.copy(
                        lines = para.lines.subList(0, linesToKeep),
                        isEnd = false,
                    ))
                }
                val linesToMove = paraLines - linesToKeep
                if (linesToMove > 0) {
                    leftParas.add(para.copy(
                        lines = para.lines.subList(linesToKeep, paraLines),
                        isFirstWordEnlarged = false,
                        firstWord = null,
                        firstWordStyle = null,
                        isContinuation = true,
                    ))
                }
                linesUsed = targetRightLines
            }
        }

        return rightParas.toList() to leftParas.toList()
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
