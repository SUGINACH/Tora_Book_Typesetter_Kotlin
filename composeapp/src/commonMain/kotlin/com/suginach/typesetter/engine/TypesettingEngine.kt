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

            // תוכן עניינים מפורט (אם מופעל)
            if (settings.enableDetailedToc) {
                // TODO: יצירת עמוד תוכן עניינים מפורט
            }

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
                p
            }

            allPages.addAll(processedPages)
            pageNumber += processedPages.size

            // איסוף הערות שוליים — מפתח String (חוק ברזל: איסור Int כמפתח)
            for (page in processedPages) {
                page.footnoteArea?.let { area ->
                    allFootnotes[page.index.toString()] = area.entries
                }
            }
        }

        // שער ראשי (אם מופעל)
        if (settings.showMainShaar) {
            val mainShaarPage = createMainShaarPage(0, metadata, settings)
            allPages.add(0, mainShaarPage)
        }

        // שלב 9: ביקורת איכות
        emit(TypesetProgress.Auditing(0))
        val auditReport = auditor.audit(allPages, settings)

        val result = TypesetResult(
            pages = allPages,
            footnotes = allFootnotes,
            endnotes = allEndnotes,
            auditReport = auditReport,
        )

        emit(TypesetProgress.Done(result))
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
        return RenderedPage(
            index = pageNum,
            pageNumber = gematriaConverter.toGematria(pageNum),
            role = PageRole.SHAAR_MAIN,
        )
    }

    private fun createSubShaarPage(pageNum: Int, partTitle: String, metadata: BookMetadata, settings: TypesetterSettings): RenderedPage {
        return RenderedPage(
            index = pageNum,
            pageNumber = gematriaConverter.toGematria(pageNum),
            role = PageRole.SHAAR_SUB,
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
