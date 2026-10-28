package com.suginach.typesetter.parser

import com.suginach.typesetter.model.*
import com.suginach.typesetter.settings.TypesetterSettings

/**
 * פרסר טקסט גולמי → רשימת טוקנים.
 *
 * זיהוי מזהים:
 * - כותרות: "כותרת N - <text>" (מקף חובה)
 * - הערות שוליים: [<text>] (תוחמים ניתנים לשינוי)
 * - הערות סיום: שורה המתחילה בקידומת
 * - הערות עריכה: ##<text>## (מוסרות לחלוטין)
 * - Metadata: שורות בתחילת המסמך (שם הספר - ..., וכו')
 * - סגנונות מותאמים: לפי קידומת/תוחמים מוגדרים
 */
class TextParser(private val settings: TypesetterSettings) {

    private val headingRegex = Regex("""^כותרת\s*(\d+)\s*[-–—]\s*(.+)$""")
    private val editorialNoteRegex = Regex("""##.+?##""")

    fun parse(rawText: String, customStyles: List<TextStyleDefinition> = emptyList()): Pair<List<Token>, BookMetadata> {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val tokens = mutableListOf<Token>()
        val metadata = mutableMapOf<String, String>()
        var inMetadata = true
        var metadataEnded = false

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // ניקוי הערות עריכה
            val cleanedLine = editorialNoteRegex.replace(trimmed, "").trim()
            if (cleanedLine.isEmpty()) {
                if (inMetadata && metadata.isNotEmpty()) {
                    metadataEnded = true
                }
                continue
            }

            // Metadata (רק בתחילת המסמך, לפני הכותרת הראשונה)
            if (inMetadata && !metadataEnded) {
                val metaMatch = parseMetadataLine(cleanedLine)
                if (metaMatch != null) {
                    metadata[metaMatch.first] = metaMatch.second
                    continue
                } else {
                    inMetadata = false
                }
            }

            // זיהוי כותרת
            val headingMatch = headingRegex.find(cleanedLine)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].toIntOrNull() ?: continue
                val text = headingMatch.groupValues[2].trim()
                tokens.add(Token.HeadingToken(index, level, text))
                continue
            }

            // זיהוי הערת סיום
            if (cleanedLine.startsWith(settings.endnotePrefix)) {
                val endnoteText = cleanedLine.removePrefix(settings.endnotePrefix)
                    .replace(Regex("^[:\\-–—]\\s*"), "").trim()
                tokens.add(Token.ParagraphToken(
                    lineIndex = index,
                    rawText = "",
                    inlines = emptyList(),
                    endNote = endnoteText,
                ))
                continue
            }

            // זיהוי סגנונים מותאמים אישית (linePrefix)
            val customStyle = customStyles.firstOrNull { style ->
                style.trigger is TextTrigger.LinePrefix && cleanedLine.startsWith(style.trigger.prefix)
            }
            if (customStyle != null && customStyle.trigger is TextTrigger.LinePrefix) {
                val text = cleanedLine.removePrefix(customStyle.trigger.prefix).trim()
                tokens.add(Token.ParagraphToken(
                    lineIndex = index,
                    rawText = text,
                    inlines = parseInlines(text),
                    customStyleId = customStyle.id,
                ))
                continue
            }

            // פסקה רגילה — ניתוח הערות שוליים ותוחמים
            val inlines = parseInlines(cleanedLine)
            tokens.add(Token.ParagraphToken(
                lineIndex = index,
                rawText = cleanedLine,
                inlines = inlines,
            ))
        }

        val bookMetadata = BookMetadata(
            title = metadata["title"] ?: "",
            subtitle = metadata["subtitle"] ?: "",
            description = metadata["description"] ?: "",
            sections = metadata["sections"] ?: "",
            author = metadata["author"] ?: "",
            year = metadata["year"] ?: "",
            backCopyright = metadata["backCopyright"] ?: "",
            backPublishing = metadata["backPublishing"] ?: "",
            backContact = metadata["backContact"] ?: "",
            backExtra = metadata["backExtra"] ?: "",
        )

        return Pair(tokens.toList(), bookMetadata)
    }

    private val metadataPatterns = mapOf(
        "title" to Regex("""^(?:שם הספר[:\s]*)?שם הספר\s*[-–—:]\s*(.+)$"""),
        "subtitle" to Regex("""^(?:נושא(?:\\|/)כותרת משנה[:\s]*)?נושא\s*[-–—:]\s*(.+)$"""),
        "description" to Regex("""^(?:נושא מפורט(?:\\|/)תיאור[:\s]*)?נושא מפורט\s*[-–—:]\s*(.+)$"""),
        "sections" to Regex("""^(?:פרטי מהדורא(?:\\|/)חלקים[:\s]*)?פרטי מהדורא\s*[-–—:]\s*(.+)$"""),
        "author" to Regex("""^(?:שם המחבר[:\s]*)?שם המחבר\s*[-–—:]\s*(.+)$"""),
        "year" to Regex("""^(?:מקום ושנת הוצאה[:\s]*)?מקום ושנת הוצאה\s*[-–—:]\s*(.+)$"""),
        "backCopyright" to Regex("""^(?:שמירת זכויות[:\s]*)?שמירת זכויות\s*[-–—:]\s*(.+)$"""),
        "backPublishing" to Regex("""^(?:פרטי הוצאה לאור[:\s]*)?פרטי הוצאה לאור\s*[-–—:]\s*(.+)$"""),
        "backContact" to Regex("""^(?:פרטי קשר[:\s]*)?פרטי קשר\s*[-–—:]\s*(.+)$"""),
        "backExtra" to Regex("""^(?:פרטים נוספים[:\s]*)?פרטים נוספים\s*[-–—:]\s*(.+)$"""),
    )

    private fun parseMetadataLine(line: String): Pair<String, String>? {
        for ((key, regex) in metadataPatterns) {
            val match = regex.find(line)
            if (match != null) {
                return Pair(key, match.groupValues[1].trim())
            }
        }
        return null
    }

    /**
     * ניתוח הערות שוליים בתוך טקסט — זיהוי תוחמים עם תמיכה בסוגריים מקוננים.
     */
    fun parseInlines(text: String): List<InlineSegment> {
        val segments = mutableListOf<InlineSegment>()
        val open = settings.footnoteDelimOpen
        val close = settings.footnoteDelimClose
        var i = 0
        val textBuf = StringBuilder()

        while (i < text.length) {
            if (text.startsWith(open, i)) {
                // חיפוש סגירה מאוזנת (תמיכה בקינון)
                if (textBuf.isNotEmpty()) {
                    segments.add(InlineSegment.Text(textBuf.toString()))
                    textBuf.clear()
                }
                var depth = 1
                var j = i + open.length
                val noteContent = StringBuilder()
                while (j < text.length && depth > 0) {
                    if (text.startsWith(open, j)) {
                        depth++
                        noteContent.append(open)
                        j += open.length
                    } else if (text.startsWith(close, j)) {
                        depth--
                        if (depth > 0) {
                            noteContent.append(close)
                        }
                        j += close.length
                    } else {
                        noteContent.append(text[j])
                        j++
                    }
                }
                if (depth == 0) {
                    segments.add(InlineSegment.Footnote(
                        content = noteContent.toString().trim(),
                        marker = "",  // יוקצה מאוחר יותר ע"י FootnoteManager
                    ))
                    i = j
                } else {
                    // סוגריים לא מאוזנים — טקסט רגיל
                    textBuf.append(open)
                    i += open.length
                }
            } else {
                textBuf.append(text[i])
                i++
            }
        }
        if (textBuf.isNotEmpty()) {
            segments.add(InlineSegment.Text(textBuf.toString()))
        }
        return segments.toList()
    }
}
