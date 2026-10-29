package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/**
 * מדידת טקסט אמיתית דרך Canvas 2D API.
 *
 * משתמש ב-context.measureText() של HTML5 Canvas למדידה מדויקת
 * של רוחב טקסט בפיקסלים, כולל התחשבות בפונט, גודל, ומשקל.
 */
actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val ctx: dynamic = canvas.getContext("2d")

    // Cache למדידות — מונע מדידות חוזרות של אותה מילה
    private val widthCache = mutableMapOf<String, Double>()

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        val cacheKey = "$text|$fontFamily|$fontSizePt|$fontWeight"
        widthCache[cacheKey]?.let { return it }

        // הגדרת הפונט ב-context
        val weight = if (fontWeight == "bold" || fontWeight == "700") "bold" else "normal"
        ctx.font = "$weight ${fontSizePt}px $fontFamily, sans-serif"

        // מדידה אמיתית דרך Canvas API
        val metrics = ctx.measureText(text)
        val width: Double = metrics.width as Double

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
