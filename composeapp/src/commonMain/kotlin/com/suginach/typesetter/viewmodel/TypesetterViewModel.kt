package com.suginach.typesetter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.suginach.typesetter.engine.TypesettingEngine
import com.suginach.typesetter.engine.TypesetProgress
import com.suginach.typesetter.model.TypesetInput
import com.suginach.typesetter.model.TypesetResult
import com.suginach.typesetter.platform.WasmJsTextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel משותף לניהול מצב האפליקציה.
 *
 * חוק ברזל (מוקש 1 חלק 4): השימוש ב-viewModelScope במקום rememberCoroutineScope
 * כדי למנוע ביטול ריצה במעבר כרטיסיות. ה-viewModelScope נשאר חי גם אם
 * המשתמש מחליף כרטיסיות בתוכנה.
 *
 * חוק ברזל (מוקש 2 חלק 4): כל עדכוני state עוברים דרך פונקציות אירועים
 * מוגדרות היטב, לא דרך למבדות ישירות.
 */
class TypesetterViewModel {
    /** CoroutineScope שנשאר חי גם במעבר כרטיסיות */
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val engine = TypesettingEngine(WasmJsTextMeasurer())

    // State חשוף ל-UI
    private val _rawText = mutableStateOf("")
    val rawText: State<String> = _rawText

    private val _settings = mutableStateOf(TypesetterSettings.default())
    val settings: State<TypesetterSettings> = _settings

    private val _typesetResult = mutableStateOf<TypesetResult?>(null)
    val typesetResult: State<TypesetResult?> = _typesetResult

    private val _isTypesetting = mutableStateOf(false)
    val isTypesetting: State<Boolean> = _isTypesetting

    private val _progressText = mutableStateOf("")
    val progressText: State<String> = _progressText

    private val _fontsLoaded = mutableStateOf(false)
    val fontsLoaded: State<Boolean> = _fontsLoaded

    private val _currentTab = mutableStateOf(Tab.EDITOR)
    val currentTab: State<Tab> = _currentTab

    /** עדכון טקסט גולמי */
    fun updateRawText(text: String) {
        _rawText.value = text
    }

    /** עדכון הגדרות */
    fun updateSettings(settings: TypesetterSettings) {
        _settings.value = settings
    }

    /** סימון שהפונטים נטענו */
    fun markFontsLoaded() {
        _fontsLoaded.value = true
    }

    /** עדכון כרטיסייה נוכחית */
    fun updateTab(tab: Tab) {
        _currentTab.value = tab
    }

    /**
     * הפעלת עימוד — רץ ב-viewModelScope שלא מתבטל במעבר כרטיסיות.
     * העימוד עצמו רץ על Dispatchers.Default (חוק 4 — מניעת קפיאת UI).
     */
    fun startTypesetting() {
        if (_rawText.value.isNotBlank() && !_isTypesetting.value && _fontsLoaded.value) {
            _isTypesetting.value = true
            _progressText.value = "מתחיל..."

            viewModelScope.launch {
                // צילום הגדרות לפני העימוד (חוק 7 — מניעת מרוץ)
                val settingsSnapshot = _settings.value
                val rawTextSnapshot = _rawText.value

                val input = TypesetInput(rawText = rawTextSnapshot)

                // מוקש 1 (חלק 2): collect על Flow ברקע
                val result = withContext(Dispatchers.Default) {
                    var finalResult: TypesetResult? = null
                    engine.typeset(input, settingsSnapshot).collect { progress ->
                        _progressText.value = when (progress) {
                            is TypesetProgress.Parsing ->
                                "מנתח טקסט... ${progress.linesProcessed}/${progress.totalLines}"
                            is TypesetProgress.PageFilling ->
                                "מעמד עמוד ${progress.currentPage}/${progress.totalPages}"
                            is TypesetProgress.Balancing ->
                                "מאזן עמוד ${progress.pageIndex}"
                            is TypesetProgress.Auditing ->
                                "בודק איכות... נמצאו ${progress.issuesFound} בעיות"
                            is TypesetProgress.Done -> {
                                finalResult = progress.result
                                "הושלם!"
                            }
                        }
                    }
                    finalResult!!
                }

                _typesetResult.value = result
                _isTypesetting.value = false
                _progressText.value = ""
                _currentTab.value = Tab.PREVIEW
            }
        }
    }

    /** ניקוי — נקרא כשה-ViewModel נהרס */
    fun dispose() {
        viewModelScope.cancel()
    }
}

/** כרטיסיות ראשיות */
enum class Tab { EDITOR, SETTINGS, AUDIT, PREVIEW }
