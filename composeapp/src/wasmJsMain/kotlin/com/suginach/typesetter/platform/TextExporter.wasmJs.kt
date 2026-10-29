package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * ייצוא טקסט ב-wasmJs דרך Blob + download link.
 */
actual class TextExporter {
    actual fun exportText(text: String, suggestedName: String) {
        // יצירת Blob עם הטקסט
        val blob = Blob(arrayOf(text).toTypedArray(), BlobPropertyBag(type = "text/plain;charset=utf-8"))
        val url = URL.createObjectURL(blob)
        val a = document.createElement("a") as HTMLAnchorElement
        a.href = url
        a.download = suggestedName
        a.style.display = "none"
        document.body?.appendChild(a)
        a.click()
        document.body?.removeChild(a)
        URL.revokeObjectURL(url)
    }
}
