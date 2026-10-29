package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.parser.TextParser
import com.suginach.typesetter.platform.TextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * המנוע הראשי — פונקציה טהורה (Pure) שמקבלת קלט והגדרות.
 *
 * חוקי ברזל לדטרמיניזם ושקיפות רפרנציאלית:
 *
 * מוקש 1: איסור HashSet/HashMap — כל האוספים הם List/LinkedHashMap/LinkedHashSet
 *          כדי להבטיח סדר איטרציה קבוע מבוסס הכנסה בלבד.
 *
 * מוקש 2: Stateless — ה-engine לא מחזיק שום state פנימי או cache סטטי.
 *          כל ריצה יוצרת את כל המבנים מחדש. typeset(input, settings) תמיד
 *          תחזיר את אותה תוצאה עבור אותו קלט (שקיפות רפרנציאלית).
 *
 * מוקש 4: שוברי שוויון דטרמיניסטיים — כל פונקציות הדירוג משתמשות ב-compareBy
 *          עם ערכים מתמטיים קבועים (length, index) ולא minByOrNull בלבד.
 *
 * מוקש 1 (Flow): המנוע מחזיר Flow<TypesetProgress> קר במקום לקבל
 *          FlowCollector כפרמטר. השלב האחרון פולט את ה-TypesetResult
 *          בתוך TypesetProgress.Done. ה-UI יכול להאזין ל-Flow ולבצע collect.
 */
class TypesettingEngine(
    private val textMeasurer: TextMeasurer,
) {
    private val gematriaConverter = GematriaConverter()
    private val pageFiller = PageFiller(textMeasurer, gematriaConverter)
    private val balancer = ColumnBalancer()
    private val widowGuard = WidowOrphanGuard()
    private val verticalJustifier = VerticalJustifier()
    private val auditor = GeometryAuditor()

    /**
     * מריץ את העימוד ומחזיר Flow של התקדמות.
     * השלב האחרון פולט TypesetProgress.Done עם התוצאה הסופית.
     */
    fun typeset(
        input: TypesetInput,
        settings: TypesetterSettings,
    ): Flow<TypesetProgress> = flow {
        // שלב 1: ניתוח טקסט
        emit(TypesetProgress.Parsing(0, 0))
        val (tokens, metadata) = TextParser(settings).parse(input.rawText, input.customTextStyles)

        // שלב 2: חלוקה לחלקים
        val parts = splitIntoParts(tokens)

        // שלב 3-4: מילוי עמודים
        val allPages = mutableListOf<RenderedPage>()
        var pageNumber = settings.pageNumStartIdx
        // חוק ברזל (מוקש 4): מפתחות String בלבד לסריאליזציה
        val allFootnotes = mutableMapOf<String, List<FootnoteEntry>>()
        val allEndnotes = mutableMapOf<String, List<EndnoteEntry>>()

        for ((partIdx, part) in parts.withIndex()) {
            emit(TypesetProgress.PageFilling(allPages.size, parts.size * 10))

            // שער משנה (אם מופעל)
            if (settings.showSubShaar && part.tokens.any { it is Token.HeadingToken && it.level == 1 }) {
                val h1 = part.tokens.first { it is Token.HeadingToken && it.level == 1 } as Token.HeadingToken
                allPages.add(createSubShaarPage(pageNumber, h1.text, metadata, settings))
                pageNumber++
            }

            // תוכן עניינים מפורט (אם מופעל) — נבנה לאחר עימוד כל החלקים
            // (ראה לאחר הלולאה)

            // עימוד הטוקנים של החלק
            val (pages, partEndnotes) = pageFiller.fillPages(
                tokens = part.tokens,
                settings = settings,
                metadata = metadata,
                startPageNumber = pageNumber,
                partIndex = partIdx,
            )

            // איסוף הערות סיום — מפתח String (חוק ברזל: איסור Int כמפתח)
            if (partEndnotes.isNotEmpty()) {
                allEndnotes[partIdx.toString()] = partEndnotes
            }

            // שלב 5-6: איזון ושמירת שורות
            val processedPages = pages.map { page ->
                emit(TypesetProgress.Balancing(page.index))
                var p = page
                if (settings.balanceChapterEnd) {
                    p = balancer.balance(p, settings)
                }
                if (settings.avoidWidowsOrphans) {
                    p = widowGuard.guard(p, settings)
                }
                // יישור אנכי — רק לעמודים מלאים (מעל 75%)
                val bodyHeightPx = (settings.pageSizeHeight - settings.propMt - settings.propMb) * 3.7795
                p = verticalJustifier.justify(p, settings, bodyHeightPx)
                p
            }

            allPages.addAll(processedPages)
            pageNumber += processedPages.size

            // איסוף הערות שוליים — מפתח String (חוק ברזל: איסור Int כמפתח)
            for (page in processedPages) {
                page.bodyArea.footnoteArea?.let { area ->
                    allFootnotes[page.index.toString()] = area.entries
                }
            }
        }

        // שער ראשי (אם מופעל)
        if (settings.showMainShaar) {
            val mainShaarPage = createMainShaarPage(0, metadata, settings)
            allPages.add(0, mainShaarPage)
        }

        // בניית תוכן עניינים (אם מופעל) — לאחר עימוד כל החלקים
        val tocEntry = if (settings.enableDetailedToc || settings.enableGeneralToc) {
            buildTableOfContents(parts, allPages, settings)
        } else null

        // יצירת עמודי תוכן עניינים מעומדים (אם מופעל)
        if (tocEntry != null) {
            val tocPages = createTocPages(tocEntry, settings)
            // הוספת עמודי TOC אחרי השער הראשי (אם יש)
            val insertIndex = if (settings.showMainShaar) 1 else 0
            allPages.addAll(insertIndex, tocPages)
        }

        // שער אחורי (אם מופעל) — בסוף הספר
        if (settings.showBackShaar) {
            val backShaarPage = createBackShaarPage(allPages.size, metadata, settings)
            allPages.add(backShaarPage)
        }

        // שלב 9: ביקורת איכות
        emit(TypesetProgress.Auditing(0))
        val auditReport = auditor.audit(allPages, settings)

        val result = TypesetResult(
            pages = allPages,
            footnotes = allFootnotes,
            endnotes = allEndnotes,
            tableOfContents = tocEntry,
            auditReport = auditReport,
        )

        emit(TypesetProgress.Done(result))
    }

    /**
     * בונה תוכן עניינים מתוך הכותרות שנמצאו בטקסט.
     * משתמש במספרי העמודים שהוקצו לכל כותרת.
     */
    /**
     * יוצר עמודי תוכן עניינים מעומדים מתוך עץ TocEntry.
     * כל עמוד מכיל כותרת "תוכן עניינים" ורשימת כותרות עם מספרי עמודים.
     */
    private fun createTocPages(tocEntry: TocEntry, settings: TypesetterSettings): List<RenderedPage> {
        val pages = mutableListOf<RenderedPage>()

        // בניית פסקאות תוכן העניינים
        val tocParagraphs = mutableListOf<Paragraph>()

        // כותרת "תוכן עניינים"
        tocParagraphs.add(Paragraph(
            text = "תוכן עניינים",
            lines = listOf(ParagraphLine("תוכן עניינים", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            headingStyle = HeadingStyle(level = 0, fontSizePt = 24, isBold = true, spaceBeforePt = 20, spaceAfterPt = 30, alignment = TextAlignment.CENTER),
        ))

        // רשימת כותרות עם מספרי עמודים
        for (child in tocEntry.children) {
            // כותרת רמה 1
            val titleLine = if (child.pageNumber.isNotEmpty()) {
                "${child.title} ${child.pageNumber}"
            } else {
                child.title
            }
            tocParagraphs.add(Paragraph(
                text = titleLine,
                lines = listOf(ParagraphLine(titleLine, isFirstLine = true, isLastLine = true, alignment = TextAlignment.RIGHT)),
                headingStyle = HeadingStyle(level = 1, fontSizePt = 14, isBold = true, spaceBeforePt = 6, spaceAfterPt = 3, alignment = TextAlignment.RIGHT),
            ))

            // כותרות משנה (רמות 2-3)
            for (subChild in child.children) {
                val subTitleLine = if (subChild.pageNumber.isNotEmpty()) {
                    "${subChild.title} ${subChild.pageNumber}"
                } else {
                    subChild.title
                }
                val indent = if (subChild.level == 2) "    " else "        "
                tocParagraphs.add(Paragraph(
                    text = indent + subTitleLine,
                    lines = listOf(ParagraphLine(indent + subTitleLine, isFirstLine = true, isLastLine = true, alignment = TextAlignment.RIGHT)),
                    headingStyle = HeadingStyle(level = subChild.level, fontSizePt = 12, isBold = false, spaceBeforePt = 2, spaceAfterPt = 2, alignment = TextAlignment.RIGHT),
                ))
            }
        }

        // חלוקה לעמודים — כ-30 שורות לעמוד
        val maxLinesPerPage = 30
        val allParagraphs = tocParagraphs.toList()
        var pageNum = 0

        var startIdx = 0
        while (startIdx < allParagraphs.size) {
            val endIdx = minOf(startIdx + maxLinesPerPage, allParagraphs.size)
            val pageParagraphs = allParagraphs.subList(startIdx, endIdx).toList()

            val textArea = TextArea(blocks = listOf(TextBlock.SingleColumn(pageParagraphs)))
            val mainArea = MainArea(textArea = textArea)
            val bodyArea = BodyArea(mainArea = mainArea)

            pages.add(RenderedPage(
                index = pageNum,
                pageNumber = if (pageNum == 0) "" else gematriaConverter.toGematria(pageNum),
                role = if (pageNum == 0) PageRole.TOC_GENERAL else PageRole.TOC_DETAILED,
                bodyArea = bodyArea,
            ))

            startIdx = endIdx
            pageNum++
        }

        return pages
    }

    private fun buildTableOfContents(
        parts: List<Part>,
        pages: List<RenderedPage>,
        settings: TypesetterSettings,
    ): TocEntry {
        val rootEntries = mutableListOf<TocEntry>()

        for (part in parts) {
            // מציאת הכותרת הראשית של החלק (רמה 1)
            val h1 = part.tokens.firstOrNull { it is Token.HeadingToken && it.level == 1 }
            if (h1 == null) continue

            val h1Token = h1 as Token.HeadingToken
            // חיפוש מספר העמוד של הכותרת — לפי האינדקס
            val h1Page = pages.firstOrNull { page ->
                page.bodyArea.mainArea.textArea.blocks.any { block ->
                    when (block) {
                        is TextBlock.HeadingSpan -> block.heading.text == h1Token.text && block.heading.level == 1
                        else -> false
                    }
                }
            }
            val h1PageNum = h1Page?.pageNumber ?: ""

            // איסוף כותרות משנה (רמות 2+)
            val children = mutableListOf<TocEntry>()
            for (token in part.tokens) {
                if (token is Token.HeadingToken && token.level > 1 && token.level <= 3) {
                    val subPage = pages.firstOrNull { page ->
                        page.bodyArea.mainArea.textArea.blocks.any { block ->
                            when (block) {
                                is TextBlock.HeadingSpan -> block.heading.text == token.text && block.heading.level == token.level
                                else -> false
                            }
                        }
                    }
                    children.add(TocEntry(
                        title = token.text,
                        pageNumber = subPage?.pageNumber ?: "",
                        level = token.level,
                    ))
                }
            }

            rootEntries.add(TocEntry(
                title = h1Token.text,
                pageNumber = h1PageNum,
                level = 1,
                children = children,
            ))
        }

        return TocEntry(
            title = "תוכן עניינים",
            pageNumber = "",
            level = 0,
            children = rootEntries,
        )
    }

    private fun splitIntoParts(tokens: List<Token>): List<Part> {
        val parts = mutableListOf<Part>()
        var currentTokens = mutableListOf<Token>()

        for (token in tokens) {
            if (token is Token.HeadingToken && token.level == 1 && currentTokens.isNotEmpty()) {
                parts.add(Part(currentTokens.toList()))
                currentTokens = mutableListOf()
            }
            currentTokens.add(token)
        }
        if (currentTokens.isNotEmpty()) {
            parts.add(Part(currentTokens.toList()))
        }

        return if (parts.isEmpty()) listOf(Part(tokens)) else parts
    }

    private data class Part(val tokens: List<Token>)

    private fun createMainShaarPage(pageNum: Int, metadata: BookMetadata, settings: TypesetterSettings): RenderedPage {
        // שער ראשי — כותרת הספר במרכז, מתחתיה תת-כותרת, מחבר, ופרטי מהדורא
        val shaarParagraphs = mutableListOf<Paragraph>()

        // רווח עליון
        shaarParagraphs.add(Paragraph(
            text = "",
            lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
        ))

        // כותרת ראשית — שם הספר
        if (metadata.title.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.title,
                lines = listOf(ParagraphLine(metadata.title, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 0, fontSizePt = 28, isBold = true, spaceBeforePt = 40, spaceAfterPt = 20, alignment = TextAlignment.CENTER),
            ))
        }

        // תת-כותרת — נושא
        if (metadata.subtitle.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.subtitle,
                lines = listOf(ParagraphLine(metadata.subtitle, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 1, fontSizePt = 18, isBold = false, spaceBeforePt = 10, spaceAfterPt = 30, alignment = TextAlignment.CENTER),
            ))
        }

        // רווח
        shaarParagraphs.add(Paragraph(
            text = "",
            lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
        ))

        // שם המחבר
        if (metadata.author.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.author,
                lines = listOf(ParagraphLine(metadata.author, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 2, fontSizePt = 14, isBold = false, spaceBeforePt = 20, spaceAfterPt = 10, alignment = TextAlignment.CENTER),
            ))
        }

        // מקום ושנת הוצאה
        if (metadata.year.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.year,
                lines = listOf(ParagraphLine(metadata.year, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 3, fontSizePt = 12, isBold = false, spaceBeforePt = 5, spaceAfterPt = 40, alignment = TextAlignment.CENTER),
            ))
        }

        val textArea = TextArea(blocks = listOf(TextBlock.SingleColumn(shaarParagraphs)))
        val mainArea = MainArea(textArea = textArea)
        val bodyArea = BodyArea(mainArea = mainArea)

        return RenderedPage(
            index = pageNum,
            pageNumber = gematriaConverter.toGematria(pageNum),
            role = PageRole.SHAAR_MAIN,
            bodyArea = bodyArea,
        )
    }

    private fun createSubShaarPage(pageNum: Int, partTitle: String, metadata: BookMetadata, settings: TypesetterSettings): RenderedPage {
        // שער משנה — כותרת החלק במרכז העמוד
        val shaarParagraphs = mutableListOf<Paragraph>()

        // רווח עליון גדול
        for (i in 1..5) {
            shaarParagraphs.add(Paragraph(
                text = "",
                lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            ))
        }

        // כותרת החלק
        shaarParagraphs.add(Paragraph(
            text = partTitle,
            lines = listOf(ParagraphLine(partTitle, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            headingStyle = HeadingStyle(level = 1, fontSizePt = 24, isBold = true, spaceBeforePt = 30, spaceAfterPt = 30, alignment = TextAlignment.CENTER),
        ))

        // רווח תחתון
        for (i in 1..3) {
            shaarParagraphs.add(Paragraph(
                text = "",
                lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            ))
        }

        val textArea = TextArea(blocks = listOf(TextBlock.SingleColumn(shaarParagraphs)))
        val mainArea = MainArea(textArea = textArea)
        val bodyArea = BodyArea(mainArea = mainArea)

        return RenderedPage(
            index = pageNum,
            pageNumber = gematriaConverter.toGematria(pageNum),
            role = PageRole.SHAAR_SUB,
            bodyArea = bodyArea,
        )
    }

    /**
     * שער אחורי — בסוף הספר, מכיל פרטי זכויות יוצרים, הוצאה לאור, וקשר.
     */
    private fun createBackShaarPage(pageNum: Int, metadata: BookMetadata, settings: TypesetterSettings): RenderedPage {
        val shaarParagraphs = mutableListOf<Paragraph>()

        // רווח עליון גדול
        for (i in 1..6) {
            shaarParagraphs.add(Paragraph(
                text = "",
                lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            ))
        }

        // שמירת זכויות
        if (metadata.backCopyright.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.backCopyright,
                lines = listOf(ParagraphLine(metadata.backCopyright, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 2, fontSizePt = 11, isBold = false, spaceBeforePt = 10, spaceAfterPt = 5, alignment = TextAlignment.CENTER),
            ))
        }

        // פרטי הוצאה לאור
        if (metadata.backPublishing.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.backPublishing,
                lines = listOf(ParagraphLine(metadata.backPublishing, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 3, fontSizePt = 10, isBold = false, spaceBeforePt = 5, spaceAfterPt = 5, alignment = TextAlignment.CENTER),
            ))
        }

        // פרטי קשר
        if (metadata.backContact.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.backContact,
                lines = listOf(ParagraphLine(metadata.backContact, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 3, fontSizePt = 10, isBold = false, spaceBeforePt = 5, spaceAfterPt = 5, alignment = TextAlignment.CENTER),
            ))
        }

        // פרטים נוספים
        if (metadata.backExtra.isNotEmpty()) {
            shaarParagraphs.add(Paragraph(
                text = metadata.backExtra,
                lines = listOf(ParagraphLine(metadata.backExtra, isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
                headingStyle = HeadingStyle(level = 3, fontSizePt = 10, isBold = false, spaceBeforePt = 5, spaceAfterPt = 5, alignment = TextAlignment.CENTER),
            ))
        }

        // רווח תחתון
        for (i in 1..3) {
            shaarParagraphs.add(Paragraph(
                text = "",
                lines = listOf(ParagraphLine("", isFirstLine = true, isLastLine = true, alignment = TextAlignment.CENTER)),
            ))
        }

        val textArea = TextArea(blocks = listOf(TextBlock.SingleColumn(shaarParagraphs)))
        val mainArea = MainArea(textArea = textArea)
        val bodyArea = BodyArea(mainArea = mainArea)

        return RenderedPage(
            index = pageNum,
            pageNumber = gematriaConverter.toGematria(pageNum),
            role = PageRole.SHAAR_BACK,
            bodyArea = bodyArea,
        )
    }
}

sealed class TypesetProgress {
    data class Parsing(val linesProcessed: Int, val totalLines: Int) : TypesetProgress()
    data class PageFilling(val currentPage: Int, val totalPages: Int) : TypesetProgress()
    data class Balancing(val pageIndex: Int) : TypesetProgress()
    data class Auditing(val issuesFound: Int) : TypesetProgress()
    /** סיום — מכיל את התוצאה הסופית */
    data class Done(val result: TypesetResult) : TypesetProgress()
}
