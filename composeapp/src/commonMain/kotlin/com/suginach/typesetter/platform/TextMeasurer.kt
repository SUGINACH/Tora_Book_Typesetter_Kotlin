package com.suginach.typesetter.platform

/**
 * מודד טקסט — ממשק פלטפורמי.
 * המנוע משתמש בו כדי למדוד רוחב מילים וגובה שורות.
 *
 * חוק ברזל (מוקש 2 באפיון): מדידת רוחב האותיות והמילים לצורך
 * אלגוריתם שבירת השורות תתבצע ב-commonMain באמצעות TextMeasurer
 * המסתמך על קובץ ה-Font Asset שנפתח ב-Res.
 *
 * בעתיד: להשתמש ב-Compose TextMeasurer האמיתי דרך pre-measurement
 * ב-Main thread לפני העימוד, ולהעביר את התוצאות למנוע ברקע.
 */
interface TextMeasurer {
    fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String = "normal"): Double
    fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double
    fun breakParagraphIntoLines(text: String, colWidthPx: Double, fontFamily: String, fontSizePt: Int, lineHeight: Double): List<String>
}

expect class WasmJsTextMeasurer() : TextMeasurer
