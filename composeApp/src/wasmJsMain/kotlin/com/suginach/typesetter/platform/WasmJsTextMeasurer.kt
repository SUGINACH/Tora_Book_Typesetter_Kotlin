package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement

    private fun jsMeasure(text: String, font: String): Double {
        val ctx = canvas.getContext("2d")
        return jsMeasureImpl(canvas, text, font)
    }

    private fun jsMeasureImpl(canvas: HTMLCanvasElement, text: String, font: String): Double {
        return js("""
            var ctx = canvas.getContext('2d');
            ctx.font = font;
            return ctx.measureText(text).width;
        """) as Double
    }

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        val font = "$fontWeight ${fontSizePt}pt \"$fontFamily\", serif"
        return jsMeasure(text, font)
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
        val spaceWidth = jsMeasure(" ", font)

        for (word in words) {
            val wordWidth = jsMeasure(word, font)
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
