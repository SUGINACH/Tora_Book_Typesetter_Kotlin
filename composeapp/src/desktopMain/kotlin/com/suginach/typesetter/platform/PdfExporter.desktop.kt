package com.suginach.typesetter.platform

import java.awt.Desktop
import java.awt.print.PrinterJob

/**
 * ייצוא PDF ב-desktop דרך AWT Printing.
 */
actual class PdfExporter {
    actual fun exportToPdf() {
        val job = PrinterJob.getPrinterJob()
        if (job.printDialog()) {
            job.print()
        }
    }
}
