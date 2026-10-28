package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.parser.TextParser
import com.suginach.typesetter.platform.TextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.coroutines.flow.FlowCollector

/**
 * המנוע הראשי — פונקציה טהורה (Pure) שמקבלת קלט והגדרות, ומחזירה תוצאה.
 * רצה ב-coroutine נפרד, שולחת אותות התקדמות.
 */
class TypesettingEngine(
    private val textMeasurer: TextMeasurer,
) {
    private val parser = TextParser(TypesetterSettings.default())
    private val gematriaConverter = GematriaConverter()
    private val pageFiller = PageFiller(textMeasurer, gematriaConverter)
    private val balancer = ColumnBalancer()
    private val widowGuard = WidowOrphanGuard()
    private val auditor = GeometryAuditor()

    suspend fun typeset(
        input: TypesetInput,
        settings: TypesetterSettings,
        progress: FlowCollector<TypesetProgress>,
    ): TypesetResult {
        // שלב 1: ניתוח טקסט
        progress.emit(TypesetProgress.Parsing(0, 0))
        val (tokens, metadata) = TextParser(settings).parse(input.rawText, input.customTextStyles)

        // שלב 2: חלוקה לחלקים
        val parts = splitIntoParts(tokens)

        // שלב 3-4: מילוי עמודים
        val allPages = mutableListOf<RenderedPage>()
        var pageNumber = settings.pageNumStartIdx
        val allFootnotes = mutableMapOf<Int, List<FootnoteEntry>>()
        val allEndnotes = mutableMapOf<Int, List<EndnoteEntry>>()
        val tocEntries = mutableListOf<TocEntry>()

        for ((partIdx, part) in parts.withIndex()) {
            progress.emit(TypesetProgress.PageFilling(allPages.size, parts.size * 10))

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

            // איסוף הערות סיום
            if (partEndnotes.isNotEmpty()) {
                allEndnotes[partIdx] = partEndnotes
            }

            // שלב 5-6: איזון ושמירת שורות
            val processedPages = pages.map { page ->
                progress.emit(TypesetProgress.Balancing(page.index))
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

            // איסוף הערות שוליים
            for (page in processedPages) {
                page.footnoteArea?.let { area ->
                    allFootnotes[page.index] = area.entries
                }
            }
        }

        // שער ראשי (אם מופעל)
        if (settings.showMainShaar) {
            val mainShaarPage = createMainShaarPage(0, metadata, settings)
            allPages.add(0, mainShaarPage)
        }

        // שלב 9: ביקורת איכות
        progress.emit(TypesetProgress.Auditing(0))
        val auditReport = auditor.audit(allPages, settings)

        progress.emit(TypesetProgress.Done)

        return TypesetResult(
            pages = allPages,
            footnotes = allFootnotes,
            endnotes = allEndnotes,
            auditReport = auditReport,
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
    object Done : TypesetProgress()
}
