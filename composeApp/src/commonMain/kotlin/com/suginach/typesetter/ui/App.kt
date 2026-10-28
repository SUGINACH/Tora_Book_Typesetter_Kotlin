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
import com.suginach.typesetter.settings.TypesetterSettings

enum class Tab { EDITOR, SETTINGS, AUDIT, PREVIEW }

@Composable
fun App() {
    var currentTab by remember { mutableStateOf(Tab.EDITOR) }
    var rawText by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(TypesetterSettings.default()) }
    var typesetResult by remember { mutableStateOf<TypesetResult?>(null) }
    var isTypesetting by remember { mutableStateOf(false) }

    MaterialTheme {
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
                    onClick = { /* יופעל ע"י הפלטפורמה */ },
                    enabled = rawText.isNotBlank() && !isTypesetting
                ) {
                    Text("⚡ עמד")
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
    Button(
        onClick = { onClick(tab) },
        colors = if (isSelected) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun EditorTab(text: String, onTextChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // סרגל כלים
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("סה״כ שורות: ${text.lines().size}", fontSize = 10.sp)
            Text("סה״כ תווים: ${text.length}", fontSize = 10.sp)
        }
        // אזור עריכה
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("הדבק כאן את הטקסט הגולמי...\n\nדוגמה:\nכותרת 1 - חלק ראשון\nכותרת 2 - סימן א\nזהו טקסט רגיל [וזו הערת שוליים].\nכותרת 7 - הערת צד\nזהו עוד טקסט.") },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Right,
            ),
        )
    }
}

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
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("🔍 ביקורת עימוד", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (issues.isEmpty()) {
            Text("✅ לא נמצאו בעיות!", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Green)
        } else {
            Text("נמצאו ${issues.size} בעיות:", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            LazyColumn {
                items(issues) { issue ->
                    Card(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text("עמוד ${issue.page} · ${issue.type}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(issue.description, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
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
        Text("📖 ${result.pages.size} עמודים", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        for (page in result.pages) {
            PagePreview(page, settings)
        }
    }
}

@Composable
private fun PagePreview(page: RenderedPage, settings: TypesetterSettings) {
    val pageWidth = (settings.pageSizeWidth * 2.5).dp // מוקטן לתצוגה
    val pageHeight = (settings.pageSizeHeight * 2.5).dp

    Card(
        modifier = Modifier
            .width(pageWidth)
            .height(pageHeight)
            .padding(4.dp)
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
                    Text(headerContentToString(page.headerRow.right), fontSize = 7.sp)
                    Text(headerContentToString(page.headerRow.center), fontSize = 7.sp)
                    Text(headerContentToString(page.headerRow.left), fontSize = 7.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }

            // טורים
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    fnArea.header?.let { Text(it, fontSize = 6.sp, fontWeight = FontWeight.Bold) }
                    for (entry in fnArea.entries) {
                        Text("${entry.marker}. ${entry.text}", fontSize = 6.sp)
                    }
                }
            }

            // מספר עמוד
            page.pageNumberFooter?.let {
                Text(it, fontSize = 8.sp)
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
