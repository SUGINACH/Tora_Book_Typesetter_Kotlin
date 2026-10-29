package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.Promise

/**
 * ייבוא קובץ ב-wasmJs דרך HTML file input.
 */
actual class FileImporter {
    actual fun importTextFile(onResult: (String?) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".txt,.text,.md"
        input.style.display = "none"

        input.onchange = { _: Event ->
            val file = input.files?.get(0)
            if (file != null) {
                val reader = FileReader()
                reader.onload = { _ ->
                    onResult(reader.result as String)
                }
                reader.onerror = { _ ->
                    onResult(null)
                }
                reader.readAsText(file, "UTF-8")
            } else {
                onResult(null)
            }
            // ניקוי
            document.body?.removeChild(input)
        }

        document.body?.appendChild(input)
        input.click()
    }
}
