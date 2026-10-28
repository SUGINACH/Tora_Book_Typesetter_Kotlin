package com.suginach.typesetter.platform

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.canvas.CanvasRenderingContext2D
import org.w3c.dom.canvas.HTMLCanvasElement

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = (document.createElement("canvas") as HTMLCanvasElement)
    private val ctx = (canvas.getContext("2d") as CanvasRenderingContext2D)

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        ctx.font = "$fontWeight ${fontSizePt}pt \"$fontFamily\", serif"
        return ctx.measureText(text).width
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
        ctx.font = "normal ${fontSizePt}pt \"$fontFamily\", serif"
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0
        val spaceWidth = ctx.measureText(" ").width

        for (word in words) {
            val wordWidth = ctx.measureText(word).width
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
}
