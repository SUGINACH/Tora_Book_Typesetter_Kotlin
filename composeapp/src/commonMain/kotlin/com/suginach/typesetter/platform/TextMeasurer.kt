package com.suginach.typesetter.platform

/**
 * מודד טקסט — ממשק פלטפורמי.
 * המנוע משתמש בו כדי למדוד רוחב מילים וגובה שורות.
 */
interface TextMeasurer {
    fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String = "normal"): Double
    fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double
    fun breakParagraphIntoLines(text: String, colWidthPx: Double, fontFamily: String, fontSizePt: Int, lineHeight: Double): List<String>
}

expect class WasmJsTextMeasurer() : TextMeasurer
