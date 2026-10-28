# מערכת עימוד ספרים תורניים — Kotlin Multiplatform

פרויקט Kotlin Multiplatform לעימוד ספרי קודש תורניים.

## טכנולוגיות
- **Kotlin Multiplatform** (JVM + wasmJs)
- **Compose Multiplatform** ל-UI
- **kotlinx.serialization** ל-JSON
- **kotlinx.coroutines** לעימוד ברקע

## בנייה

### Web (GitHub Pages)
```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```
התוצאה ב-`composeApp/build/dist/wasmJs/productionExecutable/`

### Desktop (Windows)
```bash
./gradlew :composeApp:packageMsi
```

## מבנה הפרויקט
- `composeApp/src/commonMain/` — קוד משותף (מנוע, מודלים, פרסר, UI)
- `composeApp/src/desktopMain/` — נקודת כניסה לדסקטופ (JVM)
- `composeApp/src/wasmJsMain/` — נקודת כניסה ל-Web (Wasm)

## אפיון
האפיון התפקודי המלא: `Kotlin_Technical_Specification.md`
