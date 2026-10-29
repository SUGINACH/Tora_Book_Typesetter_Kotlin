package com.suginach.typesetter.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * ייבוא קובץ ב-desktop דרך JFileChooser.
 */
actual class FileImporter {
    actual fun importTextFile(onResult: (String?) -> Unit) {
        val chooser = JFileChooser()
        chooser.fileFilter = FileNameExtensionFilter("קובצי טקסט (*.txt, *.md)", "txt", "text", "md")
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file: File = chooser.selectedFile
            try {
                val content = file.readText(charset("UTF-8"))
                onResult(content)
            } catch (e: Exception) {
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }
}
