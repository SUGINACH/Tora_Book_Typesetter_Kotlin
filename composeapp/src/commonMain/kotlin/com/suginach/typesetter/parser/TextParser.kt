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

    /**
     * חוק 2: איסור Regex מורכב — מנוע ה-Regex של ג'אווה בדסקטופ
     * ושל דפדפנים ב-Wasm מתנהגים אחרת. שימוש ב-Lookbehind או יוניקוד
     * מורכב יקרוס בזמן ריצה בדפדפן. לכן כל הפרסור מתבצע עם
     * startsWith/removePrefix/split בלבד.
     */
    private val headingPrefix = "כותרת"
    private val editorialNoteMarker = "##"

    fun parse(rawText: String, customStyles: List<TextStyleDefinition> = emptyList()): Pair<List<Token>, BookMetadata> {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val tokens = mutableListOf<Token>()
        val metadata = mutableMapOf<String, String>()
        var inMetadata = true
        var metadataEnded = false

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // ניקוי הערות עריכה — ##...## מוסרות לחלוטין (ללא Regex)
            val cleanedLine = removeEditorialNotes(trimmed).trim()
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

            // זיהוי כותרת — "כותרת N - <text>" ללא Regex
            val headingMatch = parseHeading(cleanedLine)
            if (headingMatch != null) {
                val (level, text) = headingMatch
                tokens.add(Token.HeadingToken(index, level, text))
                continue
            }

            // זיהוי הערת סיום
            if (cleanedLine.startsWith(settings.endnotePrefix)) {
                val endnoteText = cleanedLine.removePrefix(settings.endnotePrefix)
                    .let { removeLeadingSeparators(it) }
                    .trim()
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

    /**
     * זיהוי כותרת "כותרת N - <text>" ללא Regex.
     * פורמט: "כותרת" + רווחים אופציונליים + מספר + רווחים + מקף (- – —) + רווחים + טקסט
     */
    private fun parseHeading(line: String): Pair<Int, String>? {
        if (!line.startsWith(headingPrefix)) return null
        var rest = line.removePrefix(headingPrefix)

        // דילוג על רווחים
        rest = rest.trimStart()
        if (rest.isEmpty()) return null

        // חילוץ המספר
        val numEnd = rest.indexOfFirst { !it.isDigit() }
        if (numEnd <= 0) return null
        val level = rest.substring(0, numEnd).toIntOrNull() ?: return null
        rest = rest.substring(numEnd)

        // דילוג על רווחים
        rest = rest.trimStart()
        if (rest.isEmpty()) return null

        // חילוץ המקף (- – —)
        val firstChar = rest.first()
        if (firstChar != '-' && firstChar != '–' && firstChar != '—') return null
        rest = rest.substring(1)

        // דילוג על רווחים והחזרת הטקסט
        val text = rest.trimStart()
        if (text.isEmpty()) return null

        return Pair(level, text)
    }

    /**
     * הסרת הערות עריכה ##...## ללא Regex.
     * עובר שורה-שורה ומסיר תוכן בין ## ל-##.
     */
    private fun removeEditorialNotes(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '#' && text[i + 1] == '#') {
                // חיפוש ה-## הבא
                val closeIdx = findSubstring(text, editorialNoteMarker, i + 2)
                if (closeIdx >= 0) {
                    // דלג על התוכן בין ## ל-##
                    i = closeIdx + 2
                } else {
                    // אין סגירה — נשאר כפי שהוא
                    result.append(text[i])
                    i++
                }
            } else {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }

    /**
     * חיפוש תת-מחרוזת ללא Regex.
     */
    private fun findSubstring(text: String, sub: String, fromIndex: Int): Int {
        if (sub.isEmpty()) return fromIndex
        for (i in fromIndex..text.length - sub.length) {
            var match = true
            for (j in sub.indices) {
                if (text[i + j] != sub[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * הסרת מפרידים מובילים (: - – —) ורווחים ללא Regex.
     */
    private fun removeLeadingSeparators(text: String): String {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ':' || c == '-' || c == '–' || c == '—' || c.isWhitespace()) {
                i++
            } else {
                break
            }
        }
        return if (i < text.length) text.substring(i) else ""
    }

    /**
     * Metadata keys והקידומות שלהם — ללא Regex.
     */
    private val metadataKeys = listOf(
        "title" to listOf("שם הספר"),
        "subtitle" to listOf("נושא"),
        "description" to listOf("נושא מפורט"),
        "sections" to listOf("פרטי מהדורא"),
        "author" to listOf("שם המחבר"),
        "year" to listOf("מקום ושנת הוצאה"),
        "backCopyright" to listOf("שמירת זכויות"),
        "backPublishing" to listOf("פרטי הוצאה לאור"),
        "backContact" to listOf("פרטי קשר"),
        "backExtra" to listOf("פרטים נוספים"),
    )

    private fun parseMetadataLine(line: String): Pair<String, String>? {
        for ((key, prefixes) in metadataKeys) {
            for (prefix in prefixes) {
                if (line.startsWith(prefix)) {
                    var rest = line.removePrefix(prefix).trimStart()
                    // דילוג על תווי ":" אופציונליים
                    if (rest.startsWith(":")) {
                        rest = rest.removePrefix(":").trimStart()
                    }
                    // דילוג על מקף (- – —)
                    if (rest.isNotEmpty()) {
                        val first = rest.first()
                        if (first == '-' || first == '–' || first == '—') {
                            rest = rest.substring(1).trimStart()
                        }
                    }
                    if (rest.isNotEmpty()) {
                        return Pair(key, rest)
                    }
                }
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
