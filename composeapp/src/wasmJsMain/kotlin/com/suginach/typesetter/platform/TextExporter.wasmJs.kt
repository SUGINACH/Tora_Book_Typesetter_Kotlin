package com.suginach.typesetter.platform

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL

/**
 * ייצוא טקסט ב-wasmJs דרך Blob + download link.
 * משתמש ב-@JsFun כי Blob constructor לא זמין ישירות ב-wasmJs.
 */
@JsFun("(text, suggestedName) => { var blob = new Blob([text], {type: 'text/plain;charset=utf-8'}); var url = URL.createObjectURL(blob); var a = document.createElement('a'); a.href = url; a.download = suggestedName; a.style.display = 'none'; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url); }")
private external fun downloadTextFile(text: String, suggestedName: String)

actual class TextExporter {
    actual fun exportText(text: String, suggestedName: String) {
        downloadTextFile(text, suggestedName)
    }
}
