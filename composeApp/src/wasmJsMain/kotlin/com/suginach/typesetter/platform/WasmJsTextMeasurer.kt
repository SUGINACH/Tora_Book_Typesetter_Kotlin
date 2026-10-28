package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.get

actual class WasmJsTextMeasurer : TextMeasurer {
    private val canvas = document.createElement("canvas") as HTMLCanvasElement
    private val ctx = canvas.getContext("2d")!!

    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double {
        ctx.asDynamic().font = "$fontWeight ${fontSizePt}pt \"$fontFamily\", serif"
        return ctx.asDynamic().measureText(text).width as Double
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
        ctx.asDynamic().font = "normal ${fontSizePt}pt \"$fontFamily\", serif"
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentWidth = 0.0
        val spaceWidth = (ctx.asDynamic().measureText(" ").width as Double)

        for (word in words) {
            val wordWidth = (ctx.asDynamic().measureText(word).width as Double)
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
