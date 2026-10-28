package com.suginach.typesetter.platform

// Desktop doesn't use WasmJsTextMeasurer — stub implementation
actual class WasmJsTextMeasurer : TextMeasurer {
    override fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String): Double = 0.0
    override fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double = 0.0
    override fun breakParagraphIntoLines(text: String, colWidthPx: Double, fontFamily: String, fontSizePt: Int, lineHeight: Double): List<String> = emptyList()
}
