package com.suginach.typesetter.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.suginach.typesetter.model.*
import com.suginach.typesetter.settings.TypesetterSettings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import com.suginach.typesetter.composeapp.generated.resources.Res
import com.suginach.typesetter.composeapp.generated.resources.frankruehl_regular
import com.suginach.typesetter.composeapp.generated.resources.frankruehl_medium
import com.suginach.typesetter.composeapp.generated.resources.frankruehl_bold
import com.suginach.typesetter.viewmodel.TypesetterViewModel
import com.suginach.typesetter.viewmodel.Tab

/**
 * גופן עברי ראשי — Frank-Ruehl הנטען דרך Compose Resources.
 * שלושה משקלים: Regular, Medium, Bold.
 *
 * ה-extension properties על Res.font (frankruehl_regular/medium/bold) נוצרות
 * אוטומטית ע"י Compose Resources plugin וחייבות import נפרד.
 */
@Composable
fun hebrewFont(): FontFamily {
    return FontFamily(
        Font(Res.font.frankruehl_regular, FontWeight.Normal),
        Font(Res.font.frankruehl_medium, FontWeight.Medium),
        Font(Res.font.frankruehl_bold, FontWeight.Bold),
    )
}

@Composable
fun App() {
    // חוק ברזל (מוקש 1 חלק 4): שימוש ב-ViewModel במקום rememberCoroutineScope
    // כדי למנוע ביטול ריצה במעבר כרטיסיות
    val viewModel = remember { TypesetterViewModel() }

    // קריאת state מה-ViewModel
    val currentTab by viewModel.currentTab
    val rawText by viewModel.rawText
    val settings by viewModel.settings
    val typesetResult by viewModel.typesetResult
    val isTypesetting by viewModel.isTypesetting
    val progressText by viewModel.progressText
    val fontsLoaded by viewModel.fontsLoaded

    val font = hebrewFont()

    // חוק 3: המתנה לטעינת פונט לפני איפשור עימוד
    LaunchedEffect(font) {
        kotlinx.coroutines.delay(100)
        viewModel.markFontsLoaded()
    }

    // אילוץ כיוון ימני (RTL) עבור כל ה-UI — חיוני לעברית
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(typography = MaterialTheme.typography.let { it.copy(
            bodyLarge = it.bodyLarge.copy(fontFamily = font, textAlign = TextAlign.Right),
            bodyMedium = it.bodyMedium.copy(fontFamily = font, textAlign = TextAlign.Right),
            bodySmall = it.bodySmall.copy(fontFamily = font, textAlign = TextAlign.Right),
            titleLarge = it.titleLarge.copy(fontFamily = font, textAlign = TextAlign.Right),
            titleMedium = it.titleMedium.copy(fontFamily = font, textAlign = TextAlign.Right),
            titleSmall = it.titleSmall.copy(fontFamily = font, textAlign = TextAlign.Right),
            labelLarge = it.labelLarge.copy(fontFamily = font, textAlign = TextAlign.Right),
            labelMedium = it.labelMedium.copy(fontFamily = font, textAlign = TextAlign.Right),
            labelSmall = it.labelSmall.copy(fontFamily = font, textAlign = TextAlign.Right),
            headlineLarge = it.headlineLarge.copy(fontFamily = font, textAlign = TextAlign.Right),
            headlineMedium = it.headlineMedium.copy(fontFamily = font, textAlign = TextAlign.Right),
            headlineSmall = it.headlineSmall.copy(fontFamily = font, textAlign = TextAlign.Right),
        )}) {
        Column(modifier = Modifier.fillMaxSize()) {
            // סרגל כרטיסיות
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabButton("✍️ עורך", Tab.EDITOR, currentTab) { viewModel.updateTab(it) }
                TabButton("⚙️ הגדרות", Tab.SETTINGS, currentTab) { viewModel.updateTab(it) }
                TabButton("🔍 בדיקה", Tab.AUDIT, currentTab) { viewModel.updateTab(it) }
                TabButton("📖 תצוגה", Tab.PREVIEW, currentTab) { viewModel.updateTab(it) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.startTypesetting() },
                    enabled = rawText.isNotBlank() && !isTypesetting && fontsLoaded
                ) {
                    Text(if (isTypesetting) "⏳ $progressText" else "⚡ עמד")
                }
            }

            HorizontalDivider()

            // תוכן הכרטיסייה
            when (currentTab) {
                Tab.EDITOR -> EditorTab(rawText) { viewModel.updateRawText(it) }
                Tab.SETTINGS -> SettingsTab(settings) { viewModel.updateSettings(it) }
                Tab.AUDIT -> AuditTab(typesetResult)
                Tab.PREVIEW -> PreviewTab(typesetResult, settings)
            }
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
    val fileImporter = remember { com.suginach.typesetter.platform.FileImporter() }
    val scope = rememberCoroutineScope()

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
                onClick = {
                    scope.launch {
                        fileImporter.importTextFile { content ->
                            if (content != null) {
                                onTextChange(content)
                            }
                        }
                    }
                },
                modifier = Modifier.padding(2.dp),
            ) {
                Text("📂 ייבוא", fontSize = 10.sp)
            }
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

        // תוכן עניינים
        result.tableOfContents?.let { toc ->
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📋 תוכן עניינים", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    TocEntryPreview(toc)
                }
            }
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

    // צבע רקע מיוחד לשערים
    val backgroundColor = when (page.role) {
        PageRole.SHAAR_MAIN, PageRole.SHAAR_BACK -> androidx.compose.ui.graphics.Color(0xFFFFF8E1)
        PageRole.SHAAR_SUB -> androidx.compose.ui.graphics.Color(0xFFF5F5DC)
        PageRole.TOC_GENERAL, PageRole.TOC_DETAILED -> androidx.compose.ui.graphics.Color(0xFFF0F4F8)
        else -> androidx.compose.ui.graphics.Color.White
    }

    Card(
        modifier = Modifier
            .width(pageWidth)
            .height(pageHeight)
            .padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. כותרת עליונה
            page.headerRow?.let { headerRow ->
                val font = hebrewFont()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(headerContentToString(headerRow.right), fontSize = 6.sp, fontFamily = font)
                    Text(headerContentToString(headerRow.center), fontSize = 6.sp, fontFamily = font)
                    Text(headerContentToString(headerRow.left), fontSize = 6.sp, fontFamily = font)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
            }

            // 2. אזור הטקסט המרכזי (body area)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val bodyArea = page.bodyArea
                val font = hebrewFont()

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
                                Text(sn.text, fontSize = 5.sp, maxLines = 3, fontFamily = font)
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
                                Text(sn.text, fontSize = 5.sp, maxLines = 3, fontFamily = font)
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
                            Text(it, fontSize = 4.sp, fontWeight = FontWeight.Bold, fontFamily = font)
                        }
                        for (entry in fnArea.entries) {
                            Text("${entry.marker}. ${entry.text}", fontSize = 4.sp, maxLines = 2, fontFamily = font)
                        }
                    }
                }
            }

            // 3. כותרת תחתונה (מספר עמוד)
            val footerFont = hebrewFont()
            page.footerRow?.let { footer ->
                Spacer(Modifier.height(2.dp))
                Text(footer.pageNumber, fontSize = 7.sp, fontFamily = footerFont)
            }
        }
    }
}

@Composable
private fun BlockPreview(block: TextBlock, settings: TypesetterSettings) {
    val font = hebrewFont()
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
                    Text("✦", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 2.dp), fontFamily = font)
                }
            }
        }
    }
}

@Composable
private fun HeadingPreview(heading: Heading) {
    val fontSize = (heading.fontSizePt * 0.7).sp
    val font = hebrewFont()
    // ריווח לפני ואחרי הכותרת
    val spaceBefore = (heading.spaceBeforePt * 0.3).dp
    val spaceAfter = (heading.spaceAfterPt * 0.3).dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = spaceBefore, bottom = spaceAfter),
        horizontalAlignment = when (heading.alignment) {
            TextAlignment.CENTER -> Alignment.CenterHorizontally
            TextAlignment.LEFT -> Alignment.Start
            TextAlignment.RIGHT -> Alignment.End
            TextAlignment.JUSTIFIED -> Alignment.CenterHorizontally
        },
    ) {
        // עיטור עליון
        if (heading.ornamentTop != null && heading.ornamentTop != "none") {
            Text("✦", fontSize = 8.sp, fontFamily = font)
        }
        // הכותרת עצמה
        val baseText = heading.text
        val withSiman = heading.simanBadge?.let { "$it — $baseText" } ?: baseText
        Text(
            withSiman,
            fontSize = fontSize,
            fontWeight = if (heading.isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = font,
        )
        // עיטור תחתון
        if (heading.ornamentBottom != null && heading.ornamentBottom != "none") {
            Text("✦", fontSize = 8.sp, fontFamily = font)
        }
        // עיטורי צד (לרמה 3)
        if (heading.ornamentRight != null && heading.ornamentRight != "none") {
            // לא ניתן להציג עיטור צד ימין ב-Column — נשתמש ב-Row
        }
    }
}

@Composable
private fun ParagraphPreview(para: Paragraph, settings: TypesetterSettings) {
    val font = hebrewFont()
    // אם זו כותרת 4/5/6 — משתמשים בעיצוב מיוחד
    val hs = para.headingStyle
    if (hs != null) {
        val fontSize = (hs.fontSizePt * 0.55).sp
        val textAlign = when (hs.alignment) {
            TextAlignment.CENTER -> TextAlign.Center
            TextAlignment.LEFT -> TextAlign.Left
            TextAlignment.RIGHT -> TextAlign.Right
            TextAlignment.JUSTIFIED -> TextAlign.Justify
        }
        Text(
            text = para.text,
            fontSize = fontSize,
            fontWeight = if (hs.isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = font,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().padding(
                top = (hs.spaceBeforePt * 0.3).dp,
                bottom = (hs.spaceAfterPt * 0.3).dp,
            ),
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        for ((idx, line) in para.lines.withIndex()) {
            val textAlign = when (line.alignment) {
                TextAlignment.CENTER -> TextAlign.Center
                TextAlignment.LEFT -> TextAlign.Left
                TextAlignment.RIGHT -> TextAlign.Right
                TextAlignment.JUSTIFIED -> TextAlign.Justify
            }
            val baseFontSize = (settings.bodySize * 0.55).sp

            // בניית annotatedString עם מילה ראשונה מוגדלת (אם רלוונטי)
            val hasFirstWord = idx == 0 && para.firstWord != null && para.firstWordStyle != null && line.text.startsWith(para.firstWord)
            val annotated = buildAnnotatedString {
                if (hasFirstWord) {
                    val fwStyle = para.firstWordStyle
                    withStyle(SpanStyle(
                        fontSize = (fwStyle.fontSizePt * 0.55).sp,
                        fontWeight = if (fwStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = font,
                        // חלון תחת מילה ראשונה — textDecoration עם קו תחתון
                        textDecoration = if (fwStyle.hasWindow) TextDecoration.Underline else null,
                    )) {
                        append(para.firstWord)
                    }
                    append(line.text.removePrefix(para.firstWord))
                } else {
                    append(line.text)
                }
            }

            Text(
                text = annotated,
                fontSize = baseFontSize,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = font,
            )
        }
    }
}

private fun headerContentToString(content: HeaderContent): String = when (content) {
    is HeaderContent.Text -> content.text
    is HeaderContent.Ornament -> "✦"
    HeaderContent.Empty -> ""
}

/**
 * תצוגה מקדימה של רשומת תוכן עניינים — רקורסיבית.
 */
@Composable
private fun TocEntryPreview(entry: TocEntry) {
    val font = hebrewFont()
    val indent = (entry.level * 12).dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = indent, top = 1.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = entry.title,
            fontSize = if (entry.level == 0) 13.sp else if (entry.level == 1) 12.sp else 11.sp,
            fontWeight = if (entry.level <= 1) FontWeight.Bold else FontWeight.Normal,
            fontFamily = font,
        )
        if (entry.pageNumber.isNotEmpty()) {
            Text(
                text = entry.pageNumber,
                fontSize = 11.sp,
                fontFamily = font,
                color = androidx.compose.ui.graphics.Color.Gray,
            )
        }
    }
    for (child in entry.children) {
        TocEntryPreview(child)
    }
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
