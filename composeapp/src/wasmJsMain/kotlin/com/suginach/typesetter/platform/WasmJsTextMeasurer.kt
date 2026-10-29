package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/**
 * מדידת טקסט אמיתית דרך Canvas 2D API.
 *
 * משתמש ב-context.measureText() של HTML5 Canvas למדידה מדויקת
 * של רוחב טקסט בפיקסלים, כולל התחשבות בפונט, גודל, ומשקל.
 *
 * ב-wasmJs אנחנו משתמשים ב-@JsFun כדי לגשת ל-Canvas 2D API
 * כי הטיפוס 'dynamic' לא נתמך ב-wasmJs.
 */
@JsFun("(text, fontFamily, fontSizePt, fontWeight) => { var canvas = document.createElement('canvas'); var ctx = canvas.getContext('2d'); var weight = (fontWeight === 'bold' || fontWeight === '700') ? 'bold' : 'normal'; ctx.font = weight + ' ' + fontSizePt + 'px ' + fontFamily + ', sans-serif'; return ctx.measureText(text).width; }")
private external fun canvasMeasureText(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double

actual class WasmJsTextMeasurer : TextMeasurer {
    // Cache למדידות — מונע מדידות חוזרות של אותה מילה
    private val widthCache = mutableMapOf<String, Double>()

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        val cacheKey = "$text|$fontFamily|$fontSizePt|$fontWeight"
        widthCache[cacheKey]?.let { return it }

        // מדידה אמיתית דרך Canvas API
        val width = canvasMeasureText(text, fontFamily, fontSizePt, fontWeight)

        widthCache[cacheKey] = width
        return width
    }

    override fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double {
        // גובה שורה = גודל פונט × 1.2 (ascent + descent) × lineHeight
        return fontSizePt * 1.2 * lineHeight
    }

    override fun breakParagraphIntoLines(
        text: String,
        colWidthPx: Double,
        fontFamily: String,
        fontSizePt: Int,
        lineHeight: Double
    ): List<String> {
        // חוק 2: פיצול לפי רווחים ללא Regex
        val words = splitByWhitespace(text).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        // מדידת רוחב רווח
        val spaceWidth = measureWidth(" ", fontFamily, fontSizePt)

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0

        for (word in words) {
            val wordWidth = measureWidth(word, fontFamily, fontSizePt)

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
