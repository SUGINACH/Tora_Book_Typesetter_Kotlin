# אפיון קוד-טכני — מערכת עימוד ספרים תורניים ב-Kotlin

> **מסמך זה מגדיר את הארכיטקטורה הטכנית לכתיבת מערכת עימוד הספרים התורניים מחדש ב-Kotlin.**
> הוא מתבסס על האפיון התפקודי ועל ניתוח הקוד הקיים (JavaScript, ~12,700 שורות) בריפו `Tora_Book_Typesetter_Gold`.

---

## תוכן עניינים

1. [סקירה כללית](#1-סקירה-כללית)
2. [מודולי המערכת](#2-מודולי-המערכת)
3. [מודל נתונים](#3-מודל-נתונים)
4. [מנוע העימוד](#4-מנוע-העימוד)
5. [מערכת ההגדרות](#5-מערכת-ההגדרות)
6. [ממשק המשתמש](#6-ממשק-המשתמש)
7. [ייבוא וייצוא](#7-ייבוא-וייצוא)
8. [בדיקות ואימות](#8-בדיקות-ואימות)
9. [סביבת הפיתוח](#9-סביבת-הפיתוח)

---

## 1. סקירה כללית

### 1.1 טכנולוגיות

| רכיב | טכנולוגיה | סיבה |
|------|-----------|------|
| שפה | Kotlin (JVM) | שקיפות רפרנציאלית, null-safety, coroutines, type safety, קריאות גבוהה |
| מנוע עימוד | Kotlin coroutines (Worker) | ריצה ברקע ללא הקפאת UI, אותות התקדמות |
| UI | Compose Desktop (JetBrains) | מודרני, declarative, cross-platform (Windows + Web via WASM) |
| רינדור עמודים | HTML/CSS ב-WebView (JCEF) | דיוק גופנים, תמיכה RTL, @media print ל-PDF |
| ייצוא PDF | Browser print-to-PDF דרך WebView | סימניות, תוכן-לחיץ, טקסט אמיתי |
| ייצוא HTML | סריאליזציה של DOM מ-WebView | זהה לחלוטין למרונדר |
| ניהול תלויות | Gradle (Kotlin DSL) | סטנדרטי ל-Kotlin |
| סריאליזציה | kotlinx.serialization | JSON עבור הגדרות, נכסים, ייבוא/ייצוא |

### 1.2 עקרונות ארכיטקטוניים

1. **שקיפות רפרנציאלית (Referential Transparency):** פונקציית העימוד הראשית היא pure function: `fun typeset(input: TypesetInput, settings: Settings): TypesetResult`. אותו קלט תמיד מניב אותה תוצאה. אין mutable state גלובלי.

2. **היעדר תופעות לוואי (No Side Effects):** מנוע העימוד אינו משנה את ה-input, את ה-settings, או את מצב ה-UI. הוא מקבל קלט ומחזיר פלט.

3. **מנוע חיצוני (Coroutine Worker):** העימוד רץ ב-coroutine נפרד על Dispatcher.Default, ושולח אותות התקדמות דרך `Flow<TypesetProgress>`.

4. **הפרדה מוחלטת בין מנוע ל-UI:** המנוע אינו יודע על קיום ה-UI. הוא מקבל מודלי נתונים ומחזיר מודלי נתונים. ה-UI מציג את התוצאה.

5. **הגדרות כמודל נתונים:** כל ההגדרות (264 במספר) מוגדרות כ-data class אחד גדול (`TypesetterSettings`), ניתן לסריאליזציה ל-JSON. אין הגדרות "מוטמעות" בקוד.

6. **הרחבה קלה:** סוגי טקסט, סוגי עמודים, כללי עימוד, וגדלי עמוד — כולם מוגדרים כ-data classes ניתנים להרחבה, לא כ-hardcoded values.

---

## 2. מודולי המערכת

```
tora-book-typesetter/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
│
├── core/                           # מנוע העימוד — pure Kotlin, אין תלות ב-UI
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── model/                  # מודלי נתונים
│       │   ├── TypesetInput.kt
│       │   ├── TypesetResult.kt
│       │   ├── TypesetterSettings.kt
│       │   ├── Token.kt
│       │   ├── Paragraph.kt
│       │   ├── Heading.kt
│       │   ├── Footnote.kt
│       │   ├── Page.kt
│       │   ├── Column.kt
│       │   ├── TextMarker.kt
│       │   ├── PageType.kt
│       │   └── TextStyle.kt
│       ├── parser/                 # פרסר טקסט גולמי → AST
│       │   ├── TextParser.kt
│       │   ├── MetadataParser.kt
│       │   └── MarkerDetector.kt
│       ├── engine/                 # מנוע העימוד
│       │   ├── TypesettingEngine.kt
│       │   ├── LineBreaker.kt
│       │   ├── ColumnBalancer.kt
│       │   ├── PageFiller.kt
│       │   ├── FootnoteManager.kt
│       │   ├── HeadingProcessor.kt
│       │   ├── WidowOrphanGuard.kt
│       │   ├── VerticalJustifier.kt
│       │   └── QualityScorer.kt
│       ├── rules/                  # כללי עימוד (ניתנים להרחבה)
│       │   ├── TypesettingRule.kt
│       │   ├── HardConstraints.kt
│       │   ├── SoftConstraints.kt
│       │   └── PriorityResolver.kt
│       └── export/                 # ייצוא (לא תלוי ב-UI)
│           ├── HtmlExporter.kt
│           └── PdfExporter.kt
│
├── ui-desktop/                     # ממשק משתמש — Compose Desktop
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── MainWindow.kt
│       ├── tabs/
│       │   ├── EditorTab.kt
│       │   ├── SettingsTab.kt
│       │   ├── AuditTab.kt
│       │   └── PreviewTab.kt
│       ├── panels/                 # פאנלים בכרטיסיית הגדרות
│       │   ├── ThemePanel.kt
│       │   ├── NotesPanel.kt
│       │   ├── HeadingsPanel.kt
│       │   ├── OrnamentsPanel.kt
│       │   ├── HeaderPanel.kt
│       │   ├── TocPanel.kt
│       │   ├── GridPanel.kt
│       │   └── ExtensionsPanel.kt
│       ├── components/             # רכיבי UI משותפים
│       │   ├── VisualPicker.kt
│       │   ├── FontPicker.kt
│       │   ├── CollapsiblePanel.kt
│       │   └── ProgressBar.kt
│       └── state/                  # State management
│           ├── AppState.kt
│           └── SettingsState.kt
│
├── renderer/                       # רינדור עמודים ב-WebView
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── PageRenderer.kt         # ממשק ל-WebView
│       ├── HtmlBuilder.kt          # בונה HTML מתוצאת עימוד
│       ├── CssBuilder.kt           # בונה CSS מהגדרות
│       └── FontLoader.kt           # טעינת גופנים
│
├── importer/                       # ייבוא קבצים
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── TextImporter.kt
│       ├── WordImporter.kt         # ייבוא .docx עם המרת סגנונות
│       └── SettingsImporter.kt
│
└── assets/                         # נכסים חיצוניים
    ├── fonts/
    ├── ornaments/
    ├── themes/
    └── default_settings.json
```

---

## 3. מודל נתונים

### 3.1 קלט העימוד

```kotlin
data class TypesetInput(
    val rawText: String,
    val metadata: BookMetadata,
    val customTextStyles: List<TextStyleDefinition>,
    val customPageTypes: List<PageTypeDefinition>,
)

data class BookMetadata(
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val sections: String = "",
    val author: String = "",
    val year: String = "",
    val backCopyright: String = "",
    val backPublishing: String = "",
    val backContact: String = "",
    val backExtra: String = "",
)
```

### 3.2 תוצאת העימוד

```kotlin
data class TypesetResult(
    val pages: List<RenderedPage>,
    val footnotes: Map<Int, List<FootnoteEntry>>,
    val endnotes: Map<Int, List<EndnoteEntry>>,
    val tableOfContents: TocEntry,
    val auditReport: AuditReport,
    val warnings: List<TypesetWarning>,
)

data class RenderedPage(
    val index: Int,
    val pageNumber: String,
    val role: PageRole,
    val headerRow: HeaderRow?,
    val columns: List<Column>,
    val footnoteArea: FootnoteArea?,
    val pageNumberFooter: String?,
    val background: BackgroundSpec?,
    val sideNotes: List<SideNote>,
)

data class Column(
    val blocks: List<Block>,
    val height: Double,
    val isBalanced: Boolean,
)

sealed class Block {
    data class TwoColumn(val rightParagraphs: List<Paragraph>, val leftParagraphs: List<Paragraph>) : Block()
    data class SingleColumn(val paragraphs: List<Paragraph>) : Block()
    data class HeadingSpan(val heading: Heading) : Block()
    data class HeadingInline(val heading: Heading) : Block()
}
```

### 3.3 טוקנים

```kotlin
sealed class Token {
    abstract val lineIndex: Int

    data class HeadingToken(
        override val lineIndex: Int,
        val level: Int,
        val text: String,
        val customStyleId: String? = null,
    ) : Token()

    data class ParagraphToken(
        override val lineIndex: Int,
        val rawText: String,
        val inlines: List<InlineSegment>,
        val sideNote: String? = null,
        val endNote: String? = null,
        val customStyleId: String? = null,
    ) : Token()
}

sealed class InlineSegment {
    data class Text(val content: String) : InlineSegment()
    data class Footnote(val content: String, val marker: String) : InlineSegment()
}
```

### 3.4 הגדרות (קטע ראשי — המודל המלא בסעיף 5)

```kotlin
@Serializable
data class TypesetterSettings(
    val activeTheme: String = "builtin_1",
    val showMainShaar: Boolean = true,
    val showBackShaar: Boolean = true,
    val showSubShaar: Boolean = true,
    val footnoteDelimiters: Pair<String, String> = "[" to "]",
    val endnotePrefix: String = "הערת סיום -",
    val noteNumberingStyle: NoteNumberingStyle = NoteNumberingStyle.GEMATRIA,
    val avoidWidowsOrphans: Boolean = true,
    val balanceChapterEnd: Boolean = true,
    val bodyFont: String = "Frank-Ruehl",
    val bodySize: Int = 12,
    val bodyLineHeight: Double = 1.34,
    val pageSize: PageSize = PageSize.A4,
    val pageNumFormat: PageNumFormat = PageNumFormat.GEMATRIA,
    // ... 264 הגדרות סך הכל
)
```

### 3.5 סוגי טקסט ועמודים מותאמים אישית

```kotlin
@Serializable
data class TextStyleDefinition(
    val id: String,
    val name: String,
    val trigger: TextTrigger,
    val style: TextStyleProperties,
    val layoutMode: LayoutMode,
    val noteConfig: NoteConfig? = null,
    val resetAt: String? = null,
)

@Serializable
sealed class TextTrigger {
    data class LinePrefix(val prefix: String) : TextTrigger()
    data class InlineMarker(val open: String, val close: String) : TextTrigger()
}

enum class LayoutMode { INLINE, SINGLE_COLUMN_WIDE, SIDE_NOTE, FOOTNOTE, ENDNOTE, NEW_LOCATION }

@Serializable
data class PageTypeDefinition(
    val id: String,
    val name: String,
    val anchor: PageAnchor,
    val background: BackgroundSpec?,
    val textBoxes: List<TextBoxSpec>,
    val images: List<ImageSpec>,
    val showPageNumber: Boolean = false,
)

enum class PageAnchor {
    BEFORE_ALL, AFTER_MAIN_SHAAR, AFTER_GENERAL_TOC,
    BEFORE_EACH_PART, AFTER_EACH_PART, END_OF_EACH_PART, AFTER_ALL
}
```

---

## 4. מנוע העימוד

### 4.1 ממשק ראשי

```kotlin
class TypesettingEngine {
    suspend fun typeset(
        input: TypesetInput,
        settings: TypesetterSettings,
        progress: FlowCollector<TypesetProgress>
    ): TypesetResult
}

sealed class TypesetProgress {
    data class Parsing(val linesProcessed: Int, val totalLines: Int) : TypesetProgress()
    data class PageFilling(val currentPage: Int, val totalPages: Int) : TypesetProgress()
    data class Balancing(val pageIndex: Int) : TypesetProgress()
    data class Auditing(val issuesFound: Int) : TypesetProgress()
    object Done : TypesetProgress()
}
```

### 4.2 שלבי העימוד (9 שלבים)

```
1. ניתוח טקסט (Parsing)
   קלט: rawText + customTextStyles → פלט: List<Token>

2. חלוקה לחלקים (Sectioning)
   קלט: List<Token> → פלט: List<Part> (לפי כותרת 1)

3. שבירת שורות (Line Breaking)
   קלט: פסקה + רוחב טור → פלט: List<Line>
   כללים: יישור דו-צדדי, מילה ראשונה, דיבור המתחיל, אין מקף

4. מילוי עמודים (Page Filling)
   קלט: List<Token> → פלט: List<Page>
   כללים: טור ימני → שמאלי → עמוד הבא; כותרות חוצות-טורים

5. איזון טורים (Column Balancing)
   סדר עדיפויות: פסקאות → שורות → ריווח → מרווח פסקאות

6. שמירת שורות (Widow/Orphan Guard)
   מניעת: אלמנות, יתומים, כותרות יתומות, אות הערה לבד

7. יישור אנכי (Vertical Justification)
   הגדלת ריווח למילוי גובה עמוד

8. הערות שוליים (Footnote Management)
   מניעת חפיפות וגלישה; איפוס מונה

9. ביקורת איכות (Audit)
   15 סוגי בעיות, דירוג חומרה
```

### 4.3 כללי עימוד ניתנים להרחבה

```kotlin
interface TypesettingRule {
    val priority: Int  // 1=קשיח, 10=רך
    fun check(candidate: PageCandidate): RuleResult
}

sealed class RuleResult {
    object Pass : RuleResult()
    data class Fail(val reason: String, val severity: Severity) : RuleResult()
}

// כללים קשיחים (priority 1-5): אובדן תוכן, כותרת יתומה, אלמנה/יתום, גלישה, חפיפות
// כללים רכים (priority 6-10): איזון טורים, מרווחים, אסתטיקה
```

### 4.4 בורר עדיפויות

```kotlin
class PriorityResolver(private val rules: List<TypesettingRule>) {
    fun selectBest(candidates: List<PageCandidate>): PageCandidate {
        // 1. פסילת מועמדים שמפרים אילוצים קשיחים
        val legal = candidates.filter { c ->
            rules.filter { it.priority <= 5 }.all { it.check(c) is RuleResult.Pass }
        }
        // 2. דירוג מועמדים חוקיים לפי אילוצים רכים
        // 3. שובר שוויון דטרמיניסטי
        return legal.minByOrNull { score(it) } ?: fallback(candidates)
    }
}
```

---

## 5. מערכת ההגדרות

```kotlin
class SettingsManager {
    private val _settings = MutableStateFlow(loadDefaultSettings())
    val settings: StateFlow<TypesetterSettings> = _settings.asStateFlow()

    fun update(transform: (TypesetterSettings) -> TypesetterSettings)
    fun saveToJson(): String
    fun loadFromJson(json: String)
    fun exportToFile(path: Path)
    fun importFromFile(path: Path)
}
```

---

## 6. ממשק המשתמש

### 4 כרטיסיות: עורך, הגדרות, בדיקה, תצוגה

```kotlin
@Composable
fun MainWindow() {
    var currentTab by remember { mutableStateOf(Tab.EDITOR) }
    Column {
        TabBar(currentTab, onTabSelected = { currentTab = it })
        when (currentTab) {
            Tab.EDITOR -> EditorTab()
            Tab.SETTINGS -> SettingsTab()
            Tab.AUDIT -> AuditTab()
            Tab.PREVIEW -> PreviewTab()
        }
    }
}
```

### רינדור ב-WebView

```kotlin
class PageRenderer(private val webView: WebView) {
    fun render(result: TypesetResult, settings: TypesetterSettings) {
        val html = HtmlBuilder().build(result, settings)
        val css = CssBuilder().build(settings)
        webView.loadHtml("<html lang='he' dir='rtl'><head><style>$css</style></head><body>$html</body></html>")
    }
}
```

---

## 7. ייבוא וייצוא

### ייבוא וורד

```kotlin
class WordImporter {
    fun import(file: Path): String {
        // Heading 1 → "כותרת 1 - <text>"
        // Heading 2 → "כותרת 2 - <text>"
        // Footnote → [<text>]
        // Body Text → פסקה רגילה
    }
}
```

### ייצוא PDF

```kotlin
class PdfExporter {
    fun export(result, settings, outputPath, includeCropMarks)
    // דרך WebView: סימניות, תוכן-לחיץ, טקסט אמיתי, סימני חיתוך
}
```

### ייצוא HTML

```kotlin
class HtmlExporter {
    fun export(result, settings, outputPath)
    // Single-file: CSS + גופנים base64 + SVG + תמונות — הכל מוטמע
    // זהה לחלוטין למסמך המרונדר
}
```

---

## 8. בדיקות ואימות

```kotlin
class GeometryAuditor {
    fun audit(result: TypesetResult, settings: TypesetterSettings): AuditReport {
        // בודק 15 סוגי בעיות:
        // WIDOW, ORPHAN, ORPHAN_HEADING, IMBALANCED, OVERFLOW,
        // TOFU, OVERLAP, SPARSE_LINE, EMPTY_COLUMN, FOOTNOTE_MARKER,
        // FONT_FALLBACK, BROKEN_BRACKETS, TEXT_OUTSIDE_RANGE,
        // SHORT_PAGE_BEFORE_H3, U_E000
    }
}
```

---

## 9. סביבת הפיתוח

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
include("core", "ui-desktop", "renderer", "importer")

// core/build.gradle.kts
plugins { kotlin("jvm"); kotlin("plugin.serialization") }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

// ui-desktop/build.gradle.kts
plugins { kotlin("jvm"); id("org.jetbrains.compose") }
dependencies {
    implementation(project(":core"))
    implementation(project(":renderer"))
    implementation(project(":importer"))
    implementation(compose.desktop.currentOs)
}
```

---

> **מסמך זה מגדיר את הארכיטקטורה הטכנית לכתיבת התוכנה ב-Kotlin.** העקרונות המרכזיים: שקיפות רפרנציאלית, היעדר תופעות לוואי, מנוע חיצוני (coroutine), הפרדה מוחלטת בין מנוע ל-UI, הגדרות כמודל נתונים, והרחבה קלה.
