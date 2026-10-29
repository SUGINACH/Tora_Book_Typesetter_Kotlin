package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement

    // Simple approximation — average char width = 0.5 × fontSize
    // This avoids the need for Canvas 2D context in wasmJs
    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        return text.length * fontSizePt * 0.55
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
        val avgCharWidth = fontSizePt * 0.55
        val charsPerLine = (colWidthPx / avgCharWidth).toInt().coerceAtLeast(10)
        // חוק 2: פיצול לפי רווחים ללא Regex — שימוש ב-split עם רווח יחיד
        val words = splitByWhitespace(text).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentLen = 0

        for (word in words) {
            val wordLen = word.length + 1 // +1 for space
            if (currentLine.isEmpty()) {
                currentLine.append(word)
                currentLen = word.length
            } else if (currentLen + wordLen <= charsPerLine) {
                currentLine.append(" ").append(word)
                currentLen += wordLen
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
                currentLen = word.length
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
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
