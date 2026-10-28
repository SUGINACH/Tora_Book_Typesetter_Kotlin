package com.suginach.typesetter.platform

import androidx.compose.ui.text.font.FontFamily

/**
 * טוען את הגופן העברי Frank-Ruehl.
 * ב-wasmJs — דרך @font-face שמוגדר ב-index.html.
 * ב-desktop — TODO: טעינה מקובץ מקומי.
 */
expect fun loadHebrewFont(): FontFamily
