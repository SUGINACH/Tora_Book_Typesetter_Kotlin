package com.suginach.typesetter.engine

/**
 * ממיר מספר לאותיות גימטריה עברית.
 * 1=א, 2=ב, ..., 10=י, 11=יא, ..., 15=טו, 16=טז, ..., 20=כ, ..., 100=ק, ..., 400=ת, 401=תא, ...
 */
class GematriaConverter {
    private val units = listOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val tens = listOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val hundreds = listOf("", "ק", "ר", "ש", "ת")

    fun toGematria(num: Int): String {
        if (num <= 0) return ""
        if (num >= 1000) {
            // עבור מספרים מעל 1000 — מסמנים אלפים בגרש לפני
            val thousands = num / 1000
            val remainder = num % 1000
            val thousandsStr = if (thousands <= 10) units[thousands] + "'" else toGematria(thousands) + "'"
            return if (remainder > 0) thousandsStr + toGematria(remainder) else thousandsStr
        }

        val h = num / 100
        val t = (num % 100) / 10
        val u = num % 10

        // טיפול מיוחד ב-15 (טו) ו-16 (טז) — לא יו-ה ולא יו-ו
        val result = when {
            num == 15 -> "טו"
            num == 16 -> "טז"
            num >= 100 && t == 0 && u == 0 -> hundreds[h]
            else -> {
                val sb = StringBuilder()
                if (h > 0) sb.append(hundreds[h])
                if (t > 0) sb.append(tens[t])
                if (u > 0) sb.append(units[u])
                sb.toString()
            }
        }

        return result
    }

    fun toGematriaWithGeresh(num: Int): String {
        val raw = toGematria(num)
        return if (raw.length == 1) "$raw'" else "$raw\""
    }
}
