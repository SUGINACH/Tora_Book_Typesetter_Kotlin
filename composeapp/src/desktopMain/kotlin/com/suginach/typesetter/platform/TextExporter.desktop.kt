package com.suginach.typesetter.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * ייצוא טקסט ב-desktop דרך JFileChooser.
 */
actual class TextExporter {
    actual fun exportText(text: String, suggestedName: String) {
        val chooser = JFileChooser()
        chooser.selectedFile = File(suggestedName)
        chooser.fileFilter = FileNameExtensionFilter("קובצי טקסט (*.txt)", "txt")
        val result = chooser.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            file.writeText(text, charset("UTF-8"))
        }
    }
}
