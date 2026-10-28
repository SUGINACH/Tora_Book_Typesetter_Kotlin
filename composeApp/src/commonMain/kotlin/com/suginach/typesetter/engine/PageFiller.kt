package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.platform.TextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings

/**
 * ממלא עמודים בטוקנים — מחלק את הטקסט לעמודים וטורים.
 */
class PageFiller(
    private val measurer: TextMeasurer,
    private val gematria: GematriaConverter,
) {
    // רוחב טור בפיקסלים (מחושב מהגדרות הגריד וגודל העמוד)
    private fun calcColWidthPx(settings: TypesetterSettings): Double {
        val pageWidthPx = settings.pageSizeWidth * 3.7795 // mm → px (96dpi)
        val totalRatio = 2 * settings.propCol + 2 * settings.propMargin + settings.propGutter + 2 * settings.propGap
        val colRatio = settings.propCol / totalRatio
        return pageWidthPx * colRatio
    }

    // גובה אזור הגוף בפיקסלים
    private fun calcBodyHeightPx(settings: TypesetterSettings): Double {
        val pageHeightPx = settings.pageSizeHeight * 3.7795
        return pageHeightPx - (settings.propMt + settings.propMb) * 3.7795
    }

    fun fillPages(
        tokens: List<Token>,
        settings: TypesetterSettings,
        metadata: BookMetadata,
        startPageNumber: Int,
        partIndex: Int,
    ): List<RenderedPage> {
        val pages = mutableListOf<RenderedPage>()
        val colWidthPx = calcColWidthPx(settings)
        val bodyHeightPx = calcBodyHeightPx(settings)
        val lineHeight = measurer.measureLineHeight(settings.bodyFont, settings.bodySize, settings.bodyLineHeight)
        val maxLinesPerCol = (bodyHeightPx / lineHeight).toInt()

        var currentRightParas = mutableListOf<Paragraph>()
        var currentLeftParas = mutableListOf<Paragraph>()
        var currentRightLines = 0
        var currentLeftLines = 0
        var currentPageNum = startPageNumber
        var pendingSideNote: String? = null
        val footnoteQueue = mutableListOf<FootnoteEntry>()
        var footnoteCounter = 0

        for (token in tokens) {
            when (token) {
                is Token.HeadingToken -> {
                    val mode = settings.headingModes[token.level] ?: "inline_2col"

                    when {
                        mode == "side_note" -> {
                            pendingSideNote = token.text
                        }
                        mode == "new_page_1col" || mode == "sub_shaar" -> {
                            // סגירת עמוד נוכחי
                            if (currentRightParas.isNotEmpty() || currentLeftParas.isNotEmpty()) {
                                pages.add(createPage(
                                    currentPageNum++, settings, metadata,
                                    currentRightParas, currentLeftParas,
                                    footnoteQueue.toList()
                                ))
                                currentRightParas = mutableListOf()
                                currentLeftParas = mutableListOf()
                                currentRightLines = 0
                                currentLeftLines = 0
                                footnoteQueue.clear()
                            }
                            // עמוד פתיחה עם הכותרת
                            val heading = createHeading(token, settings)
                            val para = Paragraph(
                                text = token.text,
                                lines = listOf(token.text),
                                isFirstWordEnlarged = false,
                            )
                            currentRightParas.add(para)
                            currentRightLines += 3 // כותרת תופסת בערך 3 שורות
                        }
                        mode == "span_1col" -> {
                            // כותרת חוצה טורים — איזון הטורים הקודמים, ואז הכותרת ברוחב מלא
                            // לעת עתה: פשוט מוסיפים את הכותרת כבלוק נפרד
                            val heading = createHeading(token, settings)
                            // TODO: איזון טורים לפני הכותרת
                        }
                        else -> { // inline_2col
                            val heading = createHeading(token, settings)
                            val headingLines = 1
                            if (currentRightLines + headingLines > maxLinesPerCol) {
                                // מעבר לטור שמאלי
                                if (currentLeftLines + headingLines > maxLinesPerCol) {
                                    // מעבר לעמוד חדש
                                    pages.add(createPage(
                                        currentPageNum++, settings, metadata,
                                        currentRightParas, currentLeftParas,
                                        footnoteQueue.toList()
                                    ))
                                    currentRightParas = mutableListOf()
                                    currentLeftParas = mutableListOf()
                                    currentRightLines = 0
                                    currentLeftLines = 0
                                    footnoteQueue.clear()
                                }
                                currentLeftParas.add(Paragraph(text = token.text, lines = listOf(token.text)))
                                currentLeftLines += headingLines
                            } else {
                                currentRightParas.add(Paragraph(text = token.text, lines = listOf(token.text)))
                                currentRightLines += headingLines
                            }
                        }
                    }
                }
                is Token.ParagraphToken -> {
                    // אם יש הערת סיום — הוסף לתור
                    if (token.endNote != null) {
                        footnoteCounter++
                        val marker = gematria.toGematriaWithGeresh(footnoteCounter)
                        footnoteQueue.add(FootnoteEntry(marker, token.endNote))
                        continue
                    }
                    if (token.rawText.isEmpty()) continue

                    // ניתוח הערות שוליים
                    val footnoteMarkers = mutableListOf<FootnoteMarker>()
                    var displayText = token.rawText
                    for (inline in token.inlines) {
                        if (inline is InlineSegment.Footnote) {
                            footnoteCounter++
                            val marker = gematria.toGematriaWithGeresh(footnoteCounter)
                            footnoteQueue.add(FootnoteEntry(marker, inline.content))
                            footnoteMarkers.add(FootnoteMarker(marker, footnoteMarkers.size))
                        }
                    }

                    // שבירת שורות
                    val lines = measurer.breakParagraphIntoLines(
                        displayText, colWidthPx,
                        settings.bodyFont, settings.bodySize, settings.bodyLineHeight
                    )

                    if (lines.isEmpty()) continue

                    val isFirstWord = lines.size >= settings.firstWordMinLines && settings.firstWordMode != "none"
                    val firstWord = if (isFirstWord) {
                        displayText.split(Regex("\\s+")).take(settings.firstWordCount).joinToString(" ")
                    } else null

                    // חלוקת השורות בין הטורים/עמודים — תומך בפיצול פסקה ארוכה
                    var remainingLines = lines
                    var isFirstChunk = true
                    while (remainingLines.isNotEmpty()) {
                        val spaceInRight = maxLinesPerCol - currentRightLines
                        val spaceInLeft = maxLinesPerCol - currentLeftLines

                        when {
                            // מקום בטור ימני
                            remainingLines.size <= spaceInRight -> {
                                val para = buildParagraphChunk(
                                    displayText, remainingLines, isFirstChunk, isFirstWord, firstWord,
                                    token.customStyleId, footnoteMarkers, isLast = true,
                                )
                                currentRightParas.add(para)
                                currentRightLines += remainingLines.size
                                remainingLines = emptyList()
                            }
                            // מקום בטור שמאלי
                            remainingLines.size <= spaceInLeft -> {
                                val para = buildParagraphChunk(
                                    displayText, remainingLines, isFirstChunk, isFirstWord, firstWord,
                                    token.customStyleId, footnoteMarkers, isLast = true,
                                )
                                currentLeftParas.add(para)
                                currentLeftLines += remainingLines.size
                                remainingLines = emptyList()
                            }
                            // אין מקום מספיק באף טור — צריך לפצל
                            else -> {
                                if (spaceInRight >= 2) {
                                    // מילוי הטור הימני
                                    val chunk = remainingLines.take(spaceInRight)
                                    val para = buildParagraphChunk(
                                        displayText, chunk, isFirstChunk, isFirstWord, firstWord,
                                        token.customStyleId, footnoteMarkers, isLast = false,
                                    )
                                    currentRightParas.add(para)
                                    currentRightLines += chunk.size
                                    remainingLines = remainingLines.drop(chunk.size)
                                    isFirstChunk = false
                                } else if (spaceInLeft >= 2) {
                                    // מילוי הטור השמאלי
                                    val chunk = remainingLines.take(spaceInLeft)
                                    val para = buildParagraphChunk(
                                        displayText, chunk, isFirstChunk, isFirstWord, firstWord,
                                        token.customStyleId, footnoteMarkers, isLast = false,
                                    )
                                    currentLeftParas.add(para)
                                    currentLeftLines += chunk.size
                                    remainingLines = remainingLines.drop(chunk.size)
                                    isFirstChunk = false
                                } else {
                                    // שני הטורים מלאים — עמוד חדש
                                    pages.add(createPage(
                                        currentPageNum++, settings, metadata,
                                        currentRightParas, currentLeftParas,
                                        footnoteQueue.toList()
                                    ))
                                    currentRightParas = mutableListOf()
                                    currentLeftParas = mutableListOf()
                                    currentRightLines = 0
                                    currentLeftLines = 0
                                    footnoteQueue.clear()
                                    // אם הפסקה עצמה ארוכה מ-maxLinesPerCol, נפצל אותה על פני מספר עמודים
                                }
                            }
                        }
                    }
                }
            }
        }

        // עמוד אחרון
        if (currentRightParas.isNotEmpty() || currentLeftParas.isNotEmpty()) {
            pages.add(createPage(
                currentPageNum++, settings, metadata,
                currentRightParas, currentLeftParas,
                footnoteQueue.toList()
            ))
        }

        return pages
    }

    private fun createHeading(token: Token.HeadingToken, settings: TypesetterSettings): Heading {
        return Heading(
            level = token.level,
            text = token.text,
            simanBadge = if (token.level == 2 && settings.h2AutoSiman) "סימן" else null,
            ornamentTop = if (token.level == 2) settings.h2OrnamentTop else null,
            ornamentBottom = if (token.level == 2) settings.h2OrnamentBottom else null,
            ornamentRight = if (token.level == 3) settings.h3OrnamentRight else null,
            ornamentLeft = if (token.level == 3) settings.h3OrnamentLeft else null,
        )
    }

    /**
     * בונה חתיכת פסקה — או פסקה שלמה, או חלק מפסקה שפוצלה בין טורים/עמודים.
     *
     * @param isFirstChunk האם זה החלק הראשון של הפסקה (מציג firstWord מוגדל)
     * @param isLast האם זה החלק האחרון (מסמן סוף פסקה)
     */
    private fun buildParagraphChunk(
        fullText: String,
        chunkLines: List<String>,
        isFirstChunk: Boolean,
        isFirstWordEnlarged: Boolean,
        firstWord: String?,
        customStyleId: String?,
        footnoteMarkers: List<FootnoteMarker>,
        isLast: Boolean,
    ): Paragraph {
        return Paragraph(
            text = fullText,
            lines = chunkLines,
            isFirstWordEnlarged = isFirstChunk && isFirstWordEnlarged,
            firstWord = if (isFirstChunk) firstWord else null,
            isContinuation = !isFirstChunk,
            isEnd = isLast,
            customStyleId = customStyleId,
            footnoteMarkers = if (isFirstChunk) footnoteMarkers else emptyList(),
        )
    }

    private fun createPage(
        pageNum: Int,
        settings: TypesetterSettings,
        metadata: BookMetadata,
        rightParas: List<Paragraph>,
        leftParas: List<Paragraph>,
        footnotes: List<FootnoteEntry>,
    ): RenderedPage {
        val displayPageNum = when (settings.pageNumFormat) {
            "gematria" -> gematria.toGematria(pageNum)
            "page_gematria" -> "דף " + gematria.toGematria(pageNum)
            "numeric" -> pageNum.toString()
            else -> gematria.toGematria(pageNum)
        }

        val headerRow = createHeaderRow(pageNum, metadata, settings)
        val footnoteArea = if (footnotes.isNotEmpty()) {
            FootnoteArea(
                header = if (settings.footnoteHeader.isNotEmpty()) settings.footnoteHeader else null,
                ruleStyle = settings.noteRuleStyle,
                columns = settings.footnoteColumns,
                entries = footnotes,
            )
        } else null

        val block = Block.TwoColumn(rightParas, leftParas)

        return RenderedPage(
            index = pageNum,
            pageNumber = displayPageNum,
            role = PageRole.REGULAR,
            headerRow = headerRow,
            columns = listOf(Column(listOf(block), 0.0, false)),
            footnoteArea = footnoteArea,
            pageNumberFooter = displayPageNum,
        )
    }

    private fun createHeaderRow(pageNum: Int, metadata: BookMetadata, settings: TypesetterSettings): HeaderRow {
        fun content(type: String): HeaderContent = when (type) {
            "h1" -> HeaderContent.Text(metadata.title)
            "book_title" -> HeaderContent.Text(metadata.title)
            "h2" -> HeaderContent.Text("")
            "h3" -> HeaderContent.Text("")
            "empty" -> HeaderContent.Empty
            else -> HeaderContent.Empty
        }
        return HeaderRow(
            right = content(settings.headerRightType),
            center = content(settings.headerCenterType),
            left = content(settings.headerLeftType),
        )
    }
}
