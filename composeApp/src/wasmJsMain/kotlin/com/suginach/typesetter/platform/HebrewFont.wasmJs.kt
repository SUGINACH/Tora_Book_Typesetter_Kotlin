package com.suginach.typesetter.platform

import androidx.compose.ui.text.font.FontFamily

/**
 * טוען את הגופן העברי Frank-Ruehl דרך @font-face ב-index.html.
 * Skiko ב-wasm משתמש בגופנים של הדפדפן, לכן שם הגופן חייב להתאים ל-@font-face.
 * משתמשים ב-FontFamily.SansSerif כ-fallback ראשוני, ומסתמכים על כך שהדפדפן
 * יטען את Frank-Ruehl דרך @font-face עם font-family: 'Frank-Ruehl'.
 *
 * הערה: FontFamily("Frank-Ruehl") לא זמין ישירות כי ה-constructor protected.
 * לכן משתמשים ב-FontFamily.SansSerif + systemFontFamily, וה-CSS body
 * קובע font-family: 'Frank-Ruehl' ש-Skiko יורש.
 */
actual fun loadHebrewFont(): FontFamily = FontFamily.SansSerif
