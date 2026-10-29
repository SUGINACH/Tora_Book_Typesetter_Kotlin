package com.suginach.typesetter.platform

/**
 * מימוש Desktop של TextMeasurer.
 * משתמש באותו approximation כמו wasmJs — חישוב רוחב לפי תווים.
 * בעתיד ניתן להחליף ב-Compose TextMeasurer האמיתי דרך AWT FontMetrics.
 */
actual class WasmJsTextMeasurer : TextMeasurer {
    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        var totalWidth = 0.0
        for (c in text) {
            totalWidth += charWidthRatio(c) * fontSizePt
        }
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
        val words = splitByWhitespace(text).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0

        for (word in words) {
            val wordWidth = measureWidth(word, fontFamily, fontSizePt)
            val spaceWidth = fontSizePt * 0.3

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

    private fun charWidthRatio(c: Char): Double {
        val code = c.code
        if (code in 0x05B0..0x05C7 || code in 0x0591..0x05AF) return 0.0

        when (c) {
            'ש', 'ט', 'נ', 'ם', 'מ', 'ע', 'צ', 'ץ' -> return 0.70
            'י', 'ו', 'ן' -> return 0.35
            ' ' -> return 0.30
        }

        if (c in 'a'..'z' || c in 'A'..'Z') {
            return when (c.lowercaseChar()) {
                'i', 'l', 'j' -> 0.30
                'm', 'w' -> 0.80
                else -> 0.55
            }
        }

        if (c in '0'..'9') return 0.55

        return when (c) {
            '.', ',', ':', ';' -> 0.30
            '\'', '\"' -> 0.30
            '(', ')', '[', ']' -> 0.35
            '-', '–', '—' -> 0.40
            else -> 0.55
        }
    }

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
