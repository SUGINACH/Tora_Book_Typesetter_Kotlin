package com.suginach.typesetter.engine

import com.suginach.typesetter.model.*
import com.suginach.typesetter.platform.TextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings

/**
 * ממלא עמודים בטוקנים — מחלק את הטקסט לעמודים, טורים, ובלוקים.
 *
 * מבנה העמוד המתקבל:
 * - RenderedPage
 *   - headerRow (כותרת עליונה)
 *   - bodyArea
 *     - mainArea
 *       - rightSideNotes (כותרות צד ימין — רמה 7)
 *       - textArea
 *         - blocks: רצף של TwoColumn (טקסט רגיל), HeadingSpan (כותרת 2/3), Divider (סוף פרק)
 *       - leftSideNotes (כותרות צד שמאל)
 *     - footnoteArea (הערות שוליים)
 *   - footerRow (מספר עמוד)
 */
class PageFiller(
    private val measurer: TextMeasurer,
    private val gematria: GematriaConverter,
) {
    fun fillPages(
        tokens: List<Token>,
        settings: TypesetterSettings,
        metadata: BookMetadata,
        startPageNumber: Int,
        partIndex: Int,
    ): Pair<List<RenderedPage>, List<EndnoteEntry>> {
        val pages = mutableListOf<RenderedPage>()
        val endnotes = mutableListOf<EndnoteEntry>()
        val colWidthPx = calcColWidthPx(settings)
        val bodyHeightPx = calcBodyHeightPx(settings)
        val lineHeight = measurer.measureLineHeight(settings.bodyFont, settings.bodySize, settings.bodyLineHeight)
        val maxLinesPerCol = (bodyHeightPx / lineHeight).toInt()

        // מצב נוכחי
        var currentBlocks = mutableListOf<TextBlock>()
        var currentRightParas = mutableListOf<Paragraph>()
        var currentLeftParas = mutableListOf<Paragraph>()
        var currentRightLines = 0
        var currentLeftLines = 0
        var currentPageNum = startPageNumber
        val footnoteQueue = mutableListOf<FootnoteEntry>()
        val rightSideNotes = mutableListOf<SideNoteEntry>()
        val leftSideNotes = mutableListOf<SideNoteEntry>()
        var footnoteCounter = 0
        var endnoteCounter = 0

        fun flushTwoColumnIfAny() {
            if (currentRightParas.isNotEmpty() || currentLeftParas.isNotEmpty()) {
                currentBlocks.add(TextBlock.TwoColumn(
                    rightParagraphs = currentRightParas.toList(),
                    leftParagraphs = currentLeftParas.toList(),
                ))
                currentRightParas = mutableListOf()
                currentLeftParas = mutableListOf()
                currentRightLines = 0
                currentLeftLines = 0
            }
        }

        fun flushPage() {
            flushTwoColumnIfAny()
            if (currentBlocks.isEmpty() && rightSideNotes.isEmpty() && leftSideNotes.isEmpty()) return

            pages.add(createPage(
                currentPageNum++, settings, metadata,
                currentBlocks.toList(),
                rightSideNotes.toList(),
                leftSideNotes.toList(),
                footnoteQueue.toList()
            ))
            currentBlocks = mutableListOf()
            rightSideNotes.clear()
            leftSideNotes.clear()
            footnoteQueue.clear()
        }

        for (token in tokens) {
            when (token) {
                is Token.HeadingToken -> {
                    val level = token.level
                    val mode = settings.headingModes[level] ?: "inline_2col"

                    when {
                        // כותרת 7 — הערת צד
                        mode == "side_note" -> {
                            // צריך להוסיף את ההערה לשוליים ולטקסט
                            rightSideNotes.add(SideNoteEntry(
                                text = token.text,
                                anchorParagraphIdx = currentRightParas.size + currentLeftParas.size,
                                side = SideNoteSide.RIGHT,
                            ))
                        }

                        // כותרת 2/3 — כותרת חוצה טורים (HeadingSpan)
                        mode == "new_page_1col" || mode == "span_1col" || mode == "sub_shaar" -> {
                            // סגירת TwoColumn הנוכחי
                            flushTwoColumnIfAny()

                            // עיטור סוף פרק — לפני כותרת 2 חדשה, אם יש תוכן קודם
                            // (סיום הקטע של כותרת 2 הקודמת)
                            if (level == 2 && currentBlocks.isNotEmpty() && settings.chapterEndOrnament != "none") {
                                currentBlocks.add(TextBlock.Divider(
                                    ornament = settings.chapterEndOrnament,
                                    isChapterEnd = true,
                                ))
                            }

                            // יצירת הכותרת
                            val heading = createHeading(token, settings)

                            // אם new_page_1col — סגירת עמוד נוכחי קודם
                            if (mode == "new_page_1col" && currentBlocks.isNotEmpty()) {
                                flushPage()
                            }

                            currentBlocks.add(TextBlock.HeadingSpan(heading))
                        }

                        // כותרות 4/5/6 — inline בתוך הטור עם עיצוב מלא
                        else -> {
                            val headingStyle = HeadingStyle(
                                level = level,
                                fontSizePt = settings.headingSizes[level] ?: 12,
                                isBold = settings.headingBold[level] ?: true,
                                spaceBeforePt = settings.headingSpaceBefore[level] ?: 4,
                                spaceAfterPt = settings.headingSpaceAfter[level] ?: 4,
                                alignment = parseAlignment(settings.headingAlignment[level] ?: "right"),
                            )
                            val headingPara = Paragraph(
                                text = token.text,
                                lines = listOf(ParagraphLine(
                                    text = token.text,
                                    isFirstLine = true,
                                    isLastLine = true,
                                    alignment = headingStyle.alignment,
                                )),
                                customStyleId = "heading_$level",
                                headingStyle = headingStyle,
                            )

                            // מיקום הכותרת בטור המתאים
                            if (currentRightLines + 1 > maxLinesPerCol) {
                                if (currentLeftLines + 1 > maxLinesPerCol) {
                                    flushTwoColumnIfAny()
                                    if (currentBlocks.isNotEmpty()) flushPage()
                                }
                                if (currentLeftParas.isNotEmpty()) {
                                    currentLeftParas.add(headingPara)
                                    currentLeftLines += 1
                                } else {
                                    currentRightParas.add(headingPara)
                                    currentRightLines += 1
                                }
                            } else {
                                currentRightParas.add(headingPara)
                                currentRightLines += 1
                            }
                        }
                    }
                }

                is Token.ParagraphToken -> {
                    // הערת סיום — נאספת בנפרד
                    if (token.endNote != null) {
                        endnoteCounter++
                        val marker = gematria.toGematriaWithGeresh(endnoteCounter)
                        endnotes.add(EndnoteEntry(marker, token.endNote))
                        continue
                    }
                    if (token.rawText.isEmpty()) continue

                    // ניתוח הערות שוליים
                    val footnoteMarkers = mutableListOf<FootnoteMarker>()
                    val displayText = buildString {
                        var charIdx = 0
                        for (inline in token.inlines) {
                            when (inline) {
                                is InlineSegment.Text -> {
                                    append(inline.content)
                                    charIdx += inline.content.length
                                }
                                is InlineSegment.Footnote -> {
                                    footnoteCounter++
                                    val marker = gematria.toGematriaWithGeresh(footnoteCounter)
                                    footnoteQueue.add(FootnoteEntry(marker, inline.content))
                                    footnoteMarkers.add(FootnoteMarker(marker, charIdx))
                                    // סמן כתב עילי בטקסט — משאיר סמן מיקום
                                    append(marker)
                                    charIdx += marker.length
                                }
                            }
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
                        // חוק 2: פיצול לפי רווחים ללא Regex
                        splitByWhitespace(displayText).take(settings.firstWordCount).joinToString(" ")
                    } else null

                    val firstWordStyle = if (isFirstWord) {
                        FirstWordStyle(
                            fontFamily = settings.firstWordFont,
                            fontSizePt = settings.bodySize * settings.firstWordSize,
                            isBold = settings.firstWordBold,
                            hasWindow = settings.firstWordHasWindow,
                            windowWidthChars = settings.firstWordWindowChars,
                        )
                    } else null

                    // בניית ParagraphLine עם מידע עיצוב
                    val paragraphLines = lines.mapIndexed { idx, lineText ->
                        ParagraphLine(
                            text = lineText,
                            isFirstLine = idx == 0,
                            isLastLine = idx == lines.lastIndex,
                            hasFirstWord = idx == 0 && isFirstWord,
                            alignment = if (idx == lines.lastIndex) {
                                parseAlignment(settings.bodyLastLineAlignment)
                            } else {
                                parseAlignment(settings.bodyAlignment)
                            },
                        )
                    }

                    // חלוקה בין טורים/עמודים
                    var remainingLines = paragraphLines
                    var isFirstChunk = true
                    while (remainingLines.isNotEmpty()) {
                        val spaceInRight = maxLinesPerCol - currentRightLines
                        val spaceInLeft = maxLinesPerCol - currentLeftLines

                        when {
                            remainingLines.size <= spaceInRight -> {
                                val para = buildParagraphChunk(
                                    displayText, remainingLines, isFirstChunk, isFirstWord, firstWord,
                                    firstWordStyle, token.customStyleId, footnoteMarkers, isLast = true,
                                )
                                currentRightParas.add(para)
                                currentRightLines += remainingLines.size
                                remainingLines = emptyList()
                            }
                            remainingLines.size <= spaceInLeft -> {
                                val para = buildParagraphChunk(
                                    displayText, remainingLines, isFirstChunk, isFirstWord, firstWord,
                                    firstWordStyle, token.customStyleId, footnoteMarkers, isLast = true,
                                )
                                currentLeftParas.add(para)
                                currentLeftLines += remainingLines.size
                                remainingLines = emptyList()
                            }
                            else -> {
                                if (spaceInRight >= 2) {
                                    val chunk = remainingLines.take(spaceInRight)
                                    val para = buildParagraphChunk(
                                        displayText, chunk, isFirstChunk, isFirstWord, firstWord,
                                        firstWordStyle, token.customStyleId, footnoteMarkers, isLast = false,
                                    )
                                    currentRightParas.add(para)
                                    currentRightLines += chunk.size
                                    remainingLines = remainingLines.drop(chunk.size)
                                    isFirstChunk = false
                                } else if (spaceInLeft >= 2) {
                                    val chunk = remainingLines.take(spaceInLeft)
                                    val para = buildParagraphChunk(
                                        displayText, chunk, isFirstChunk, isFirstWord, firstWord,
                                        firstWordStyle, token.customStyleId, footnoteMarkers, isLast = false,
                                    )
                                    currentLeftParas.add(para)
                                    currentLeftLines += chunk.size
                                    remainingLines = remainingLines.drop(chunk.size)
                                    isFirstChunk = false
                                } else {
                                    flushTwoColumnIfAny()
                                    flushPage()
                                }
                            }
                        }
                    }
                }
            }
        }

        // עמוד אחרון + עיטור סוף פרק
        flushTwoColumnIfAny()
        if (settings.chapterEndOrnament != "none") {
            currentBlocks.add(TextBlock.Divider(
                ornament = settings.chapterEndOrnament,
                isChapterEnd = true,
            ))
        }
        flushPage()

        return Pair(pages, endnotes)
    }

    private fun parseAlignment(s: String): TextAlignment = when (s) {
        "left" -> TextAlignment.LEFT
        "right" -> TextAlignment.RIGHT
        "center" -> TextAlignment.CENTER
        "justified" -> TextAlignment.JUSTIFIED
        else -> TextAlignment.JUSTIFIED
    }

    private fun calcColWidthPx(settings: TypesetterSettings): Double {
        val pageWidthPx = settings.pageSizeWidth * 3.7795 // mm → px (96dpi)
        val totalRatio = 2 * settings.propCol + 2 * settings.propMargin + settings.propGutter + 2 * settings.propGap
        val colRatio = settings.propCol / totalRatio
        return pageWidthPx * colRatio
    }

    private fun calcBodyHeightPx(settings: TypesetterSettings): Double {
        val pageHeightPx = settings.pageSizeHeight * 3.7795
        return pageHeightPx - (settings.propMt + settings.propMb) * 3.7795
    }

    /**
     * חוק 2: פיצול טקסט לפי רווחים (space, tab, newline) ללא Regex.
     */
    private fun splitByWhitespace(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            if (c.isWhitespace()) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun createHeading(token: Token.HeadingToken, settings: TypesetterSettings): Heading {
        val level = token.level
        return Heading(
            level = level,
            text = token.text,
            fontFamily = settings.headingFonts[level] ?: "Frank-Ruehl",
            fontSizePt = settings.headingSizes[level] ?: 14,
            isBold = settings.headingBold[level] ?: true,
            spaceBeforePt = settings.headingSpaceBefore[level] ?: 0,
            spaceAfterPt = settings.headingSpaceAfter[level] ?: 0,
            alignment = parseAlignment(settings.headingAlignment[level] ?: "center"),
            simanBadge = if (level == 2 && settings.h2AutoSiman) "סימן" else null,
            ornamentTop = if (level == 2) settings.h2OrnamentTop else null,
            ornamentBottom = if (level == 2) settings.h2OrnamentBottom else null,
            ornamentRight = if (level == 3) settings.h3OrnamentRight else null,
            ornamentLeft = if (level == 3) settings.h3OrnamentLeft else null,
        )
    }

    private fun buildParagraphChunk(
        fullText: String,
        chunkLines: List<ParagraphLine>,
        isFirstChunk: Boolean,
        isFirstWordEnlarged: Boolean,
        firstWord: String?,
        firstWordStyle: FirstWordStyle?,
        customStyleId: String?,
        footnoteMarkers: List<FootnoteMarker>,
        isLast: Boolean,
    ): Paragraph {
        return Paragraph(
            text = fullText,
            lines = chunkLines,
            isFirstWordEnlarged = isFirstChunk && isFirstWordEnlarged,
            firstWord = if (isFirstChunk) firstWord else null,
            firstWordStyle = if (isFirstChunk) firstWordStyle else null,
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
        blocks: List<TextBlock>,
        rightSideNotes: List<SideNoteEntry>,
        leftSideNotes: List<SideNoteEntry>,
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

        val textArea = TextArea(blocks = blocks, isBalanced = false)
        val mainArea = MainArea(
            rightSideNotes = rightSideNotes,
            textArea = textArea,
            leftSideNotes = leftSideNotes,
        )
        val bodyArea = BodyArea(
            mainArea = mainArea,
            footnoteArea = footnoteArea,
        )
        val footerRow = FooterRow(
            pageNumber = displayPageNum,
            alignment = parseAlignment(when (settings.pageNumPosition) {
                "bottom_center" -> "center"
                "bottom_left" -> "left"
                "bottom_right" -> "right"
                else -> "center"
            }),
            frameStyle = settings.pageNumFrame,
        )

        return RenderedPage(
            index = pageNum,
            pageNumber = displayPageNum,
            role = PageRole.REGULAR,
            headerRow = headerRow,
            bodyArea = bodyArea,
            footerRow = footerRow,
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

        // כותרות מתחלפות — עמודים אי-זוגיים מציגים ימין, זוגיים מציגים שמאל
        if (settings.headerAlternating) {
            return if (pageNum % 2 == 1) {
                // עמוד אי-זוגי — כותרת ימין
                HeaderRow(
                    right = content(settings.headerRightType),
                    center = HeaderContent.Empty,
                    left = HeaderContent.Empty,
                )
            } else {
                // עמוד זוגי — כותרת שמאל
                HeaderRow(
                    right = HeaderContent.Empty,
                    center = HeaderContent.Empty,
                    left = content(settings.headerLeftType),
                )
            }
        }

        return HeaderRow(
            right = content(settings.headerRightType),
            center = content(settings.headerCenterType),
            left = content(settings.headerLeftType),
        )
    }
}
