package com.suginach.typesetter.platform

/**
 * מודד טקסט — ממשק פלטפורמי.
 * המנוע משתמש בו כדי למדוד רוחב מילים וגובה שורות.
 *
 * דסקטופ: מימוש עם java.awt.FontMetrics או Canvas.
 * Web: מימוש עם Canvas API של הדפדפן.
 */
interface TextMeasurer {
    /**
     * מודד רוחב טקסט בפיקסלים.
     */
    fun measureWidth(text: String, fontFamily: String, fontSizePt: Int, fontWeight: String = "normal"): Double

    /**
     * מודד גובה שורה בפיקסלים.
     */
    fun measureLineHeight(fontFamily: String, fontSizePt: Int, lineHeight: Double): Double

    /**
     * שובר פסקה לשורות לפי רוחב טור נתון.
     * מחזיר רשימה של שורות (כל שורה = מחרוזת).
     */
    fun breakParagraphIntoLines(text: String, colWidthPx: Double, fontFamily: String, fontSizePt: Int, lineHeight: Double): List<String>
}
