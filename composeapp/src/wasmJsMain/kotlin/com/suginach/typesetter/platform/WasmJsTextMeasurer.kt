package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement

    /**
     * מדידת רוחב טקסט — approximation משופר המתחשב בתווים רחבים/צרים בעברית.
     *
     * מוקש 2 באפיון: בעתיד יש להחליף ב-Compose TextMeasurer אמיתי.
     * כרגע משתמשים ב-approximation כי Compose TextMeasurer דורש @Composable
     * context ולא יכול לרוץ ברקע על Dispatchers.Default.
     *
     * רוחב יחסי לגודל פונט:
     * - אותיות רגילות: 0.55
     * - אותיות רחבות (ש, ט, נ, ם, מ): 0.70
     * - אותיות צרות (י, ו, ן): 0.35
     * - ניקוד וטעמים: 0.0 (לא תופסים רוחב אופקי משמעותי)
     */
    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        var totalWidth = 0.0
        for (c in text) {
            totalWidth += charWidthRatio(c) * fontSizePt
        }
        // הוספת רוחב Bold
        if (fontWeight == "bold" || fontWeight == "700") {
            totalWidth *= 1.05
        }
        return totalWidth
    }

    override fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double {
        return fontSizePt * 1.333 * lineHeight
    }

    override fun breakParagraphIntoLines(
        text: String,
        colWidthPx: Double,
        fontFamily: String,
        fontSizePt: Int,
        lineHeight: Double
    ): List<String> {
        // חוק 2: פיצול לפי רווחים ללא Regex — שימוש ב-split עם רווח יחיד
        val words = splitByWhitespace(text).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0

        for (word in words) {
            val wordWidth = measureWidth(word, fontFamily, fontSizePt)
            val spaceWidth = fontSizePt * 0.3 // רוחב רווח

            if (currentLine.isEmpty()) {
                currentLine.append(word)
                currentWidth = wordWidth
            } else if (currentWidth + spaceWidth + wordWidth <= colWidthPx) {
                currentLine.append(" ").append(word)
                currentWidth += spaceWidth + wordWidth
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
                currentWidth = wordWidth
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * רוחב יחסי של תו ביחס לגודל פונט.
     * מבוסס על Frank-Ruehl — פונט עברי רגיל.
     */
    private fun charWidthRatio(c: Char): Double {
        // ניקוד וטעמים — לא תופסים רוחב אופקי
        val code = c.code
        if (code in 0x05B0..0x05C7 || code in 0x0591..0x05AF) return 0.0

        // אותיות רחבות בעברית
        when (c) {
            'ש', 'ט', 'נ', 'ם', 'מ', 'ע', 'צ', 'ץ' -> return 0.70
            // אותיות צרות בעברית
            'י', 'ו', 'ן' -> return 0.35
            // רווח
            ' ' -> return 0.30
        }

        // אותיות לטיניות — רוחב ממוצע
        if (c in 'a'..'z' || c in 'A'..'Z') {
            return when (c.lowercaseChar()) {
                'i', 'l', 'j' -> 0.30
                'm', 'w' -> 0.80
                else -> 0.55
            }
        }

        // ספרות
        if (c in '0'..'9') return 0.55

        // סימני פיסוק — טיפול ידני כדי למנוע בעיות escape
        return when (c) {
            '.', ',', ':', ';' -> 0.30
            '\'', '\"' -> 0.30
            '(', ')', '[', ']' -> 0.35
            '-', '–', '—' -> 0.40
            else -> 0.55
        }
    }

    /**
     * פיצול טקסט לפי רווחים (space, tab, newline) ללא Regex.
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
}
