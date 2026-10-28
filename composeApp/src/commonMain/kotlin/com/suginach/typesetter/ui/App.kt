package com.suginach.typesetter.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.suginach.typesetter.model.*
import com.suginach.typesetter.engine.TypesettingEngine
import com.suginach.typesetter.engine.TypesetProgress
import com.suginach.typesetter.platform.WasmJsTextMeasurer
import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import com.suginach.typesetter.resources.Res

enum class Tab { EDITOR, SETTINGS, AUDIT, PREVIEW }

/**
 * גופן עברי ראשי — Frank-Ruehl הנטען דרך Compose Resources.
 * שלושה משקלים: Regular, Medium, Bold.
 */
@Composable
fun hebrewFont(): FontFamily {
    return FontFamily(
        Font(Res.font.frankruehlregular, FontWeight.Normal),
        Font(Res.font.frankruehlmedium, FontWeight.Medium),
        Font(Res.font.frankruehlbold, FontWeight.Bold),
    )
}

@Composable
fun App() {
    var currentTab by remember { mutableStateOf(Tab.EDITOR) }
    var rawText by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(TypesetterSettings.default()) }
    var typesetResult by remember { mutableStateOf<TypesetResult?>(null) }
    var isTypesetting by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val engine = remember { TypesettingEngine(WasmJsTextMeasurer()) }
    val font = hebrewFont()

    MaterialTheme(typography = MaterialTheme.typography.let { it.copy(
        bodyLarge = it.bodyLarge.copy(fontFamily = font),
        bodyMedium = it.bodyMedium.copy(fontFamily = font),
        bodySmall = it.bodySmall.copy(fontFamily = font),
        titleLarge = it.titleLarge.copy(fontFamily = font),
        titleMedium = it.titleMedium.copy(fontFamily = font),
        titleSmall = it.titleSmall.copy(fontFamily = font),
        labelLarge = it.labelLarge.copy(fontFamily = font),
        labelMedium = it.labelMedium.copy(fontFamily = font),
        labelSmall = it.labelSmall.copy(fontFamily = font),
        headlineLarge = it.headlineLarge.copy(fontFamily = font),
        headlineMedium = it.headlineMedium.copy(fontFamily = font),
        headlineSmall = it.headlineSmall.copy(fontFamily = font),
    )}) {
        Column(modifier = Modifier.fillMaxSize()) {
            // סרגל כרטיסיות
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabButton("✍️ עורך", Tab.EDITOR, currentTab) { currentTab = it }
                TabButton("⚙️ הגדרות", Tab.SETTINGS, currentTab) { currentTab = it }
                TabButton("🔍 בדיקה", Tab.AUDIT, currentTab) { currentTab = it }
                TabButton("📖 תצוגה", Tab.PREVIEW, currentTab) { currentTab = it }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (rawText.isNotBlank() && !isTypesetting) {
                            isTypesetting = true
                            progressText = "מתחיל..."
                            scope.launch {
                                val input = TypesetInput(rawText = rawText)
                                val result = engine.typeset(input, settings) { progress ->
                                    progressText = when (progress) {
                                        is TypesetProgress.Parsing -> "מנתח טקסט... ${progress.linesProcessed}/${progress.totalLines}"
                                        is TypesetProgress.PageFilling -> "מעמד עמוד ${progress.currentPage}/${progress.totalPages}"
                                        is TypesetProgress.Balancing -> "מאזן עמוד ${progress.pageIndex}"
                                        is TypesetProgress.Auditing -> "בודק איכות... נמצאו ${progress.issuesFound} בעיות"
                                        TypesetProgress.Done -> "הושלם!"
                                    }
                                }
                                typesetResult = result
                                isTypesetting = false
                                progressText = ""
                                currentTab = Tab.PREVIEW
                            }
                        }
                    },
                    enabled = rawText.isNotBlank() && !isTypesetting
                ) {
                    Text(if (isTypesetting) "⏳ $progressText" else "⚡ עמד")
                }
            }

            HorizontalDivider()

            // תוכן הכרטיסייה
            when (currentTab) {
                Tab.EDITOR -> EditorTab(rawText) { rawText = it }
                Tab.SETTINGS -> SettingsTab(settings) { settings = it }
                Tab.AUDIT -> AuditTab(typesetResult)
                Tab.PREVIEW -> PreviewTab(typesetResult, settings)
            }
        }
    }
}

@Composable
private fun TabButton(label: String, tab: Tab, current: Tab, onClick: (Tab) -> Unit) {
    val isSelected = current == tab
    if (isSelected) {
        Button(
            onClick = { onClick(tab) },
            modifier = Modifier.padding(2.dp)
        ) {
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = { onClick(tab) },
            modifier = Modifier.padding(2.dp)
        ) {
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EditorTab(text: String, onTextChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // סרגל כלים
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("סה״כ שורות: ${text.lines().size}", fontSize = 10.sp)
            Text("סה״כ תווים: ${text.length}", fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onTextChange(sampleText) },
                modifier = Modifier.padding(2.dp),
            ) {
                Text("📄 טען דוגמה", fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = { onTextChange("") },
                modifier = Modifier.padding(2.dp),
                enabled = text.isNotEmpty(),
            ) {
                Text("🗑 נקה", fontSize = 10.sp)
            }
        }
        // אזור עריכה
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("הדבק כאן את הטקסט הגולמי...\n\nדוגמה:\nכותרת 1 - חלק ראשון\nכותרת 2 - סימן א\nזהו טקסט רגיל [וזו הערת שוליים].\nכותרת 7 - הערת צד\nזהו עוד טקסט.\n\nאו לחץ על \"טען דוגמה\" למעלה.") },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = hebrewFont(),
                fontSize = 11.sp,
                textAlign = TextAlign.Right,
            ),
        )
    }
}

/**
 * טקסט דוגמה להדגמת יכולות המערכת — מכיל כותרות מכל הרמות, הערות שוליים, והערות סיום.
 */
private val sampleText = """שם הספר - ספר הדוגמה
נושא - הדגמת מערכת עימוד
נושא מפורט - דוגמה מלאה לכל סוגי התוכן
פרטי מהדורא - מהדורה ראשונה
שם המחבר - מחבר לדוגמה
מקום ושנת הוצאה - ירושלים תשפ"ו

כותרת 1 - חלק ראשון: מבוא לעימוד תורני
כותרת 2 - סימן א: כללי העימוד
כותרת 3 - פרק ראשון: מבוא
זהו טקסט רגיל המיועד להדגים את יכולות מערכת העימוד. המערכת תקרא את הטקסט הגולמי, תזהה את הכותרות לפי הדפוס "כותרת N - שם", ותחלק את הטקסט לעמודים ולטורים באופן אוטומטי. הערות שוליים מופיעות בתוך סוגריים מרובעים [כך], והן יופרדו מהטקסט ויוצגו בתחתית העמוד.

זוהי פסקה שנייה הממחישה את יכולת המערכת לשבור שורות באופן אוטומטי בהתאם לרוחב הטור. המערכת משתמשת במודד טקסט (TextMeasurer) כדי לחשב את רוחב כל מילה ולשבור את השורות במקומות המתאימים [וזוהי הערת שוליים שנייה להדגמה]. המערכת גם תומכת בהערות סיום שמופיעות בסוף הספר.
הערת סיום - זוהי הערת סיום להדגמה, המופרדת מהטקסט הראשי ותופיע באזור הערות הסיום בסוף הספר.

כותרת 4 - תת-סעיף: דוגמה לכותרת משנית
זהו טקסט נוסף הממחיש את המשך העימוד. המערכת תמשיך למלא את הטורים עד שתגיע לתחתית העמוד, ואז תעבור לעמוד הבא אוטומטית. האיזון בין הטורים יתבצע בסוף כל פרק כדי להבטיח שהטורים יהיו באותו גובה.

כותרת 7 - הערת צד: דוגמה להערת צד
זהו טקסט נוסף שבא אחרי הערת הצד. הערת הצד תופיע בשוליים ליד הטקסט הרלוונטי. המשך הטקסט יזרום באופן טבעי וימלא את הטורים בהתאם לרוחב ולגובה המוגדרים בהגדרות.

כותרת 1 - חלק שני: פרקים מתקדמים
כותרת 2 - סימן ב: עימוד מתקדם
זהו תחילת החלק השני של הספר. שים לב שהמערכת פתחה עמוד חדש לחלק זה, בהתאם להגדרות ברירת המחדל שבהן כותרת רמה 1 פותחת עמוד חדש. המערכת גם תומכת בשערי משנה אוטומטיים, בתוכן עניינים, ובמספור עמודים בגימטריה [וזוהי הערה נוספת].

המשך הטקסט בחלק השני, המדגים את יכולת המערכת להמשיך לעמד טקסט ארוך לאורך מספר עמודים. המערכת תשמור על רצף הפסקאות, תזהה יתומות ואלמנות, ותדווח עליהן בדו"ח הביקורת. המשתמש יכול לעבור לכרטיסיית "בדיקה" כדי לראות את כל הבעיות שזוהו.
"""

@Composable
private fun SettingsTab(settings: TypesetterSettings, onSettingsChange: (TypesetterSettings) -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("⚙️ הגדרות", fontSize = 16.sp, fontWeight = FontWeight.Bold)

        // כפתור איפוס
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = { onSettingsChange(TypesetterSettings.default()) }) {
                Text("↺ איפוס לברירת מחדל", fontSize = 10.sp)
            }
        }

        // גוף הטקסט — ניתן לעריכה
        SettingsSection("גוף הטקסט") {
            IntSettingRow("גודל (pt)", settings.bodySize, 8..24) { v ->
                onSettingsChange(settings.copy(bodySize = v))
            }
            DoubleSettingRow("ריווח שורות", settings.bodyLineHeight, 1.0..2.0, 0.05) { v ->
                onSettingsChange(settings.copy(bodyLineHeight = v))
            }
            SettingRow("גופן") { Text(settings.bodyFont, fontSize = 10.sp) }
        }

        // הגדרות הערות
        SettingsSection("הערות") {
            StringSettingRow("תו פתיחה", settings.footnoteDelimOpen) { v ->
                onSettingsChange(settings.copy(footnoteDelimOpen = v))
            }
            StringSettingRow("תו סגירה", settings.footnoteDelimClose) { v ->
                onSettingsChange(settings.copy(footnoteDelimClose = v))
            }
            DropdownSettingRow("סגנון מספור", settings.noteNumberingStyle, listOf("gematria", "arabic", "alpha")) { v ->
                onSettingsChange(settings.copy(noteNumberingStyle = v))
            }
            IntSettingRow("טורים", settings.footnoteColumns, 1..3) { v ->
                onSettingsChange(settings.copy(footnoteColumns = v))
            }
        }

        // איכות עימוד
        SettingsSection("איכות עימוד") {
            BooleanSettingRow("מניעת יתומות/אלמנות", settings.avoidWidowsOrphans) { v ->
                onSettingsChange(settings.copy(avoidWidowsOrphans = v))
            }
            BooleanSettingRow("איזון סוף-פרק", settings.balanceChapterEnd) { v ->
                onSettingsChange(settings.copy(balanceChapterEnd = v))
            }
        }

        // כותרות
        SettingsSection("כותרות (0-7) — מצב עימוד") {
            val modes = listOf("inline_2col", "new_page_1col", "span_1col", "sub_shaar", "side_note")
            for (level in 0..7) {
                DropdownSettingRow(
                    "כותרת $level",
                    settings.headingModes[level] ?: "inline_2col",
                    modes,
                ) { v ->
                    val newModes = settings.headingModes.toMutableMap()
                    newModes[level] = v
                    onSettingsChange(settings.copy(headingModes = newModes))
                }
            }
        }

        // גריד
        SettingsSection("גריד וטיפוגרפיה") {
            DropdownSettingRow("גודל עמוד", "${settings.pageSizeWidth.toInt()}×${settings.pageSizeHeight.toInt()}mm",
                listOf("148×210mm (A5)", "210×297mm (A4)", "176×250mm (B5)")) { v ->
                val (w, h) = when {
                    v.startsWith("148") -> 148.0 to 210.0
                    v.startsWith("176") -> 176.0 to 250.0
                    else -> 210.0 to 297.0
                }
                onSettingsChange(settings.copy(pageSizeWidth = w, pageSizeHeight = h))
            }
            DoubleSettingRow("רוחב טור (%)", settings.propCol, 20.0..50.0, 1.0) { v ->
                onSettingsChange(settings.copy(propCol = v))
            }
            DoubleSettingRow("שוליים (%)", settings.propMargin, 3.0..15.0, 0.5) { v ->
                onSettingsChange(settings.copy(propMargin = v))
            }
            DoubleSettingRow("מרזב (%)", settings.propGutter, 1.0..5.0, 0.5) { v ->
                onSettingsChange(settings.copy(propGutter = v))
            }
            DropdownSettingRow("פורמט מספור", settings.pageNumFormat,
                listOf("gematria", "page_gematria", "numeric")) { v ->
                onSettingsChange(settings.copy(pageNumFormat = v))
            }
        }
    }
}

@Composable
private fun IntSettingRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { if (value > range.first) onChange(value - 1) },
                enabled = value > range.first,
                modifier = Modifier.padding(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
            ) { Text("−", fontSize = 10.sp) }
            Text("$value", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedButton(
                onClick = { if (value < range.last) onChange(value + 1) },
                enabled = value < range.last,
                modifier = Modifier.padding(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
            ) { Text("+", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun DoubleSettingRow(label: String, value: Double, range: ClosedFloatingPointRange<Double>, step: Double, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { if (value > range.start) onChange(((value - step) * 100).toInt() / 100.0) },
                enabled = value > range.start,
                modifier = Modifier.padding(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
            ) { Text("−", fontSize = 10.sp) }
            Text(formatDouble(value), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedButton(
                onClick = { if (value < range.endInclusive) onChange(((value + step) * 100).toInt() / 100.0) },
                enabled = value < range.endInclusive,
                modifier = Modifier.padding(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
            ) { Text("+", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun BooleanSettingRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun StringSettingRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.width(60.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
            singleLine = true,
        )
    }
}

@Composable
private fun DropdownSettingRow(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(value, fontSize = 10.sp)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for (opt in options) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(opt, fontSize = 10.sp) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp)
        value()
    }
}

@Composable
private fun AuditTab(result: TypesetResult?) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין תוצאות — הפעל עימוד תחילה", fontSize = 14.sp)
        }
        return
    }
    val issues = result.auditReport.issues
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState)) {
        Text("🔍 ביקורת עימוד", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (issues.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFE8F5E9),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("✓ אין בעיות!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                    Text("העימוד עבר את כל הבדיקות הגיאומטריות בהצלחה.", fontSize = 11.sp)
                }
            }
        } else {
            // סיכום לפי סוג
            val byType = issues.groupBy { it.type }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFFF8E1),
                ),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("סה״כ ${issues.size} בעיות ב-${issues.map { it.page }.distinct().size} עמודים:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    for ((type, typeIssues) in byType.entries.sortedByDescending { it.value.size }) {
                        val severityColor = when (typeIssues.first().severity) {
                            Severity.CRITICAL -> androidx.compose.ui.graphics.Color(0xFFC62828)
                            Severity.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
                            Severity.LOW -> androidx.compose.ui.graphics.Color(0xFF6A1B9A)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("• ${typeLabel(type)}", fontSize = 10.sp, color = severityColor)
                            Text("${typeIssues.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = severityColor)
                        }
                    }
                }
            }

            // רשימת בעיות מפורטת
            Text("פירוט:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            for (issue in issues.sortedBy { it.page }) {
                val severityColor = when (issue.severity) {
                    Severity.CRITICAL -> androidx.compose.ui.graphics.Color(0xFFC62828)
                    Severity.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
                    Severity.LOW -> androidx.compose.ui.graphics.Color(0xFF6A1B9A)
                }
                Card(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("עמוד ${issue.page}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(typeLabel(issue.type), fontSize = 9.sp, color = severityColor)
                            Text(issue.severity.name, fontSize = 8.sp, color = severityColor)
                        }
                        Text(issue.description, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: IssueType): String = when (type) {
    IssueType.WIDOW -> "אלמנה"
    IssueType.ORPHAN -> "יתומה"
    IssueType.ORPHAN_HEADING -> "כותרת יתומה"
    IssueType.IMBALANCED -> "טורים לא מאוזנים"
    IssueType.OVERFLOW -> "גלישה"
    IssueType.TOFU -> "תווים חסרים"
    IssueType.OVERLAP -> "חפיפה"
    IssueType.SPARSE_LINE -> "שורה דלילה"
    IssueType.EMPTY_COLUMN -> "טור ריק"
    IssueType.FOOTNOTE_MARKER -> "סמן הערה"
    IssueType.FONT_FALLBACK -> "גופן חלופי"
    IssueType.BROKEN_BRACKETS -> "סוגריים שבורים"
    IssueType.TEXT_OUTSIDE_RANGE -> "טקסט מחוץ לטווח"
    IssueType.SHORT_PAGE_BEFORE_H3 -> "עמוד קצר לפני כותרת"
    IssueType.U_E000 -> "תו פרטי"
}

@Composable
private fun PreviewTab(result: TypesetResult?, settings: TypesetterSettings) {
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("אין תצוגה — הפעל עימוד תחילה", fontSize = 14.sp)
        }
        return
    }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // סרגל מידע
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📖 ${result.pages.size} עמודים", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            val totalFootnotes = result.footnotes.values.sumOf { it.size }
            if (totalFootnotes > 0) {
                Text("📝 $totalFootnotes הערות שוליים", fontSize = 11.sp)
                Spacer(Modifier.width(16.dp))
            }
            val issueCount = result.auditReport.issues.size
            if (issueCount > 0) {
                Text("⚠ $issueCount בעיות", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFFB8860B))
            } else {
                Text("✓ אין בעיות", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
            }
        }
        for ((idx, page) in result.pages.withIndex()) {
            // מספר עמוד לוגי מעל כל עמוד
            Text("עמוד ${idx + 1}", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)
            PagePreview(page, settings)
        }

        // הערות סיום
        val totalEndnotes = result.endnotes.values.sumOf { it.size }
        if (totalEndnotes > 0) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📋 הערות סיום ($totalEndnotes)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    for ((partIdx, entries) in result.endnotes) {
                        if (entries.isEmpty()) continue
                        Text("— חלק ${partIdx + 1} —", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        for (entry in entries) {
                            Text("${entry.marker}. ${entry.text}", fontSize = 10.sp, modifier = Modifier.padding(vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePreview(page: RenderedPage, settings: TypesetterSettings) {
    // מוקטן פי 3 מגודל A4 אמיתי (210x297mm)
    val pageWidth = (settings.pageSizeWidth * 1.6).dp
    val pageHeight = (settings.pageSizeHeight * 1.6).dp

    Card(
        modifier = Modifier
            .width(pageWidth)
            .height(pageHeight)
            .padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. כותרת עליונה
            page.headerRow?.let { headerRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(headerContentToString(headerRow.right), fontSize = 6.sp)
                    Text(headerContentToString(headerRow.center), fontSize = 6.sp)
                    Text(headerContentToString(headerRow.left), fontSize = 6.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
            }

            // 2. אזור הטקסט המרכזי (body area)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val bodyArea = page.bodyArea

                // 2.1 אזור טקסט עיקרי + כותרות צד
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val mainArea = bodyArea.mainArea

                    // כותרות צד ימין
                    if (mainArea.rightSideNotes.isNotEmpty()) {
                        Column(
                            modifier = Modifier.width(40.dp).fillMaxHeight(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            for (sn in mainArea.rightSideNotes) {
                                Text(sn.text, fontSize = 5.sp, maxLines = 3)
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }

                    // אזור הטקסט (blocks)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (block in mainArea.textArea.blocks) {
                            BlockPreview(block, settings)
                        }
                    }

                    // כותרות צד שמאל
                    if (mainArea.leftSideNotes.isNotEmpty()) {
                        Column(
                            modifier = Modifier.width(40.dp).fillMaxHeight(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            for (sn in mainArea.leftSideNotes) {
                                Text(sn.text, fontSize = 5.sp, maxLines = 3)
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }

                // 2.2 אזור הערות שוליים
                bodyArea.footnoteArea?.let { fnArea ->
                    Column(
                        modifier = Modifier.width(50.dp).fillMaxHeight(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        fnArea.header?.let {
                            Text(it, fontSize = 4.sp, fontWeight = FontWeight.Bold)
                        }
                        for (entry in fnArea.entries) {
                            Text("${entry.marker}. ${entry.text}", fontSize = 4.sp, maxLines = 2)
                        }
                    }
                }
            }

            // 3. כותרת תחתונה (מספר עמוד)
            page.footerRow?.let { footer ->
                Spacer(Modifier.height(2.dp))
                Text(footer.pageNumber, fontSize = 7.sp)
            } ?: page.pageNumberFooter?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun BlockPreview(block: TextBlock, settings: TypesetterSettings) {
    when (block) {
        is TextBlock.TwoColumn -> {
            // שני טורים שווים ברוחבם ומאוזנים בגובהם
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // טור ימני
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    for (para in block.rightParagraphs) {
                        ParagraphPreview(para, settings)
                    }
                }
                // טור שמאלי
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    for (para in block.leftParagraphs) {
                        ParagraphPreview(para, settings)
                    }
                }
            }
        }
        is TextBlock.SingleColumn -> {
            // טור אחד לרוחב
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                block.heading?.let { HeadingPreview(it) }
                for (para in block.paragraphs) {
                    ParagraphPreview(para, settings)
                }
            }
        }
        is TextBlock.HeadingSpan -> {
            // כותרת חוצה טורים — full-width
            HeadingPreview(block.heading)
        }
        is TextBlock.Divider -> {
            // מחלק עיטורי — לסוף קטע של כותרת 2 (לפני כותרת 2 הבאה)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                val count = if (block.isChapterEnd) settings.chapterEndOrnamentCount else 1
                repeat(count) {
                    Text("✦", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun HeadingPreview(heading: Heading) {
    val fontSize = (heading.fontSizePt * 0.7).sp
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = when (heading.alignment) {
            TextAlignment.CENTER -> Alignment.CenterHorizontally
            TextAlignment.LEFT -> Alignment.Start
            TextAlignment.RIGHT -> Alignment.End
            TextAlignment.JUSTIFIED -> Alignment.CenterHorizontally
        },
    ) {
        // עיטור עליון
        if (heading.ornamentTop != null && heading.ornamentTop != "none") {
            Text("✦", fontSize = 8.sp)
        }
        // הכותרת עצמה
        val baseText = heading.text
        val withSiman = heading.simanBadge?.let { "$it — $baseText" } ?: baseText
        Text(
            withSiman,
            fontSize = fontSize,
            fontWeight = if (heading.isBold) FontWeight.Bold else FontWeight.Normal,
        )
        // עיטור תחתון
        if (heading.ornamentBottom != null && heading.ornamentBottom != "none") {
            Text("✦", fontSize = 8.sp)
        }
    }
}

@Composable
private fun ParagraphPreview(para: Paragraph, settings: TypesetterSettings) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        for ((idx, line) in para.lines.withIndex()) {
            val isLast = idx == para.lines.lastIndex
            // יישור: שורה אחרונה ממורכזת, אחרת יישור לשני צדדים (Compose יעשה את זה אוטומטית)
            val align = when (line.alignment) {
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.LEFT -> TextAlign.Left
                TextAlignment.RIGHT -> TextAlign.Right
                TextAlignment.JUSTIFIED -> TextAlign.Justify
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (line.alignment) {
                    TextAlignment.CENTER -> Arrangement.Center
                    TextAlignment.LEFT -> Arrangement.Start
                    TextAlignment.RIGHT -> Arrangement.End
                    TextAlignment.JUSTIFIED -> Arrangement.SpaceBetween
                }
            ) {
                if (idx == 0 && para.firstWord != null && para.firstWordStyle != null) {
                    // מילה ראשונה מוגדלת עם חלון
                    val fwStyle = para.firstWordStyle
                    Text(
                        para.firstWord + " ",
                        fontSize = (fwStyle.fontSizePt * 0.55).sp,
                        fontWeight = if (fwStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                    )
                    val remaining = line.text.removePrefix(para.firstWord).trimStart()
                    if (remaining.isNotEmpty()) {
                        Text(remaining, fontSize = (settings.bodySize * 0.55).sp)
                    }
                } else {
                    Text(line.text, fontSize = (settings.bodySize * 0.55).sp)
                }
            }
        }
    }
}

private fun headerContentToString(content: HeaderContent): String = when (content) {
    is HeaderContent.Text -> content.text
    is HeaderContent.Ornament -> "✦"
    HeaderContent.Empty -> ""
}

/** פורמט מספר עשרוני בלי String.format (לא זמין ב-wasmJs) */
private fun formatDouble(v: Double): String {
    val rounded = (v * 100).toInt() / 100.0
    val intPart = rounded.toInt()
    val fracPart = ((rounded - intPart) * 100).toInt()
    return if (fracPart == 0) "$intPart.00"
    else if (fracPart < 10) "$intPart.0$fracPart"
    else "$intPart.$fracPart"
}
