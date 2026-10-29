package com.suginach.typesetter.platform

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage

/**
 * מדידת טקסט אמיתית דרך AWT FontMetrics ב-desktop.
 *
 * משתמש ב-Java AWT למדידה מדויקת של רוחב טקסט בפיקסלים,
 * כולל התחשבות בפונט, גודל, ומשקל.
 */
actual class WasmJsTextMeasurer : TextMeasurer {
    private val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    private val g2d = img.createGraphics()
    private val frc = FontRenderContext(null, true, true)
    private val widthCache = mutableMapOf<String, Double>()

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        val cacheKey = "$text|$fontFamily|$fontSizePt|$fontWeight"
        widthCache[cacheKey]?.let { return it }

        val style = if (fontWeight == "bold" || fontWeight == "700") Font.BOLD else Font.PLAIN
        val font = Font(fontFamily, style, fontSizePt)
        val textLayout = java.awt.font.TextLayout(text, font, frc)
        val width = textLayout.advance

        widthCache[cacheKey] = width
        return width
    }

    override fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double {
        return fontSizePt * 1.2 * lineHeight
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
