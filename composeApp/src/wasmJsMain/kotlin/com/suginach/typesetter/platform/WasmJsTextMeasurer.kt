package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

@JsFun("canvas, text, font => { const ctx = canvas.getContext('2d'); ctx.font = font; return ctx.measureText(text).width; }")
private external fun jsMeasureText(canvas: HTMLCanvasElement, text: String, font: String): Double

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        val font = "$fontWeight ${fontSizePt}pt \"$fontFamily\", serif"
        return jsMeasureText(canvas, text, font)
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
        val font = "normal ${fontSizePt}pt \"$fontFamily\", serif"
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0
        val spaceWidth = jsMeasureText(canvas, " ", font)

        for (word in words) {
            val wordWidth = jsMeasureText(canvas, word, font)
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
