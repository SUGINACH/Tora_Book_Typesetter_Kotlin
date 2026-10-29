package com.suginach.typesetter.platform

/**
 * גופני מערכת — expect/actual.
 * ב-wasmJs: queryLocalFonts() API או רשימת גופנים מובנית.
 * ב-desktop: AWT GraphicsEnvironment.
 */
expect class SystemFontsLoader() {
    /**
     * טוען את רשימת הגופנים הזמינים במערכת.
     * @return רשימת שמות גופנים
     */
    fun loadAvailableFonts(): List<String>
}
