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

enum class Tab { EDITOR, SETTINGS, AUDIT, PREVIEW }

val hebrewFont = FontFamily.SansSerif

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

    MaterialTheme(typography = MaterialTheme.typography.let { it.copy(
        bodyLarge = it.bodyLarge.copy(fontFamily = hebrewFont),
        bodyMedium = it.bodyMedium.copy(fontFamily = hebrewFont),
        bodySmall = it.bodySmall.copy(fontFamily = hebrewFont),
        titleLarge = it.titleLarge.copy(fontFamily = hebrewFont),
        titleMedium = it.titleMedium.copy(fontFamily = hebrewFont),
        titleSmall = it.titleSmall.copy(fontFamily = hebrewFont),
        labelLarge = it.labelLarge.copy(fontFamily = hebrewFont),
        labelMedium = it.labelMedium.copy(fontFamily = hebrewFont),
        labelSmall = it.labelSmall.copy(fontFamily = hebrewFont),
        headlineLarge = it.headlineLarge.copy(fontFamily = hebrewFont),
        headlineMedium = it.headlineMedium.copy(fontFamily = hebrewFont),
        headlineSmall = it.headlineSmall.copy(fontFamily = hebrewFont),
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
                fontFamily = hebrewFont,
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

        // גוף הטקסט
        SettingsSection("גוף הטקסט") {
            SettingRow("גופן") { Text(settings.bodyFont) }
            SettingRow("גודל (pt)") { Text("${settings.bodySize}") }
            SettingRow("ריווח שורות") { Text("${settings.bodyLineHeight}") }
        }

        // הגדרות הערות
        SettingsSection("הערות") {
            SettingRow("תו פתיחה") { Text(settings.footnoteDelimOpen) }
            SettingRow("תו סגירה") { Text(settings.footnoteDelimClose) }
            SettingRow("סגנון מספור") { Text(settings.noteNumberingStyle) }
            SettingRow("טורים") { Text("${settings.footnoteColumns}") }
        }

        // איכות עימוד
        SettingsSection("איכות עימוד") {
            SettingRow("מניעת יתומות/אלמנות") { Text(if (settings.avoidWidowsOrphans) "כן" else "לא") }
            SettingRow("איזון סוף-פרק") { Text(if (settings.balanceChapterEnd) "כן" else "לא") }
        }

        // כותרות
        SettingsSection("כותרות (0-7)") {
            for (level in 0..7) {
                SettingRow("כותרת $level") {
                    Text("${settings.headingModes[level] ?: "inline_2col"} | ${settings.headingSizes[level] ?: 12}pt")
                }
            }
        }

        // גריד
        SettingsSection("גריד וטיפוגרפיה") {
            SettingRow("גודל עמוד") { Text("${settings.pageSizeWidth}×${settings.pageSizeHeight}mm") }
            SettingRow("רוחב טור (%)") { Text("${settings.propCol}") }
            SettingRow("שוליים (%)") { Text("${settings.propMargin}") }
            SettingRow("מרזב (%)") { Text("${settings.propGutter}") }
            SettingRow("פורמט מספור") { Text(settings.pageNumFormat) }
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
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // כותרת עליונה
            if (page.headerRow != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(headerContentToString(page.headerRow.right), fontSize = 6.sp)
                    Text(headerContentToString(page.headerRow.center), fontSize = 6.sp)
                    Text(headerContentToString(page.headerRow.left), fontSize = 6.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
            }

            // טורים
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (column in page.columns) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.End
                    ) {
                        for (block in column.blocks) {
                            when (block) {
                                is Block.TwoColumn -> {
                                    // טור ימני
                                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        for (para in block.rightParagraphs) {
                                            ParagraphPreview(para, settings)
                                        }
                                    }
                                    // טור שמאלי
                                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        for (para in block.leftParagraphs) {
                                            ParagraphPreview(para, settings)
                                        }
                                    }
                                }
                                is Block.SingleColumn -> {
                                    for (para in block.paragraphs) {
                                        ParagraphPreview(para, settings)
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // הערות שוליים
            page.footnoteArea?.let { fnArea ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    fnArea.header?.let { Text(it, fontSize = 5.sp, fontWeight = FontWeight.Bold) }
                    for (entry in fnArea.entries) {
                        Text("${entry.marker}. ${entry.text}", fontSize = 5.sp, maxLines = 2)
                    }
                }
            }

            // מספר עמוד
            page.pageNumberFooter?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun ParagraphPreview(para: Paragraph, settings: TypesetterSettings) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        for ((idx, line) in para.lines.withIndex()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (idx == 0 && para.firstWord != null) {
                    Text(
                        para.firstWord + " ",
                        fontSize = (settings.bodySize * 0.6).sp,
                        fontWeight = FontWeight.Bold
                    )
                    val remaining = line.removePrefix(para.firstWord).trimStart()
                    if (remaining.isNotEmpty()) {
                        Text(remaining, fontSize = (settings.bodySize * 0.55).sp)
                    }
                } else {
                    Text(line, fontSize = (settings.bodySize * 0.55).sp)
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
