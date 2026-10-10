package com.pixelsave.app.util

import com.pixelsave.app.save.ImageSaver
import java.util.Locale

object Format {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun humanSize(bytes: Long): String {
        if (bytes < 0) return "unknown"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.2f %s", value, units[unit])
    }
}

object ReportFormatter {

    fun format(results: List<ImageSaver.SaveResult>): String =
        results.joinToString(separator = "\n\n") { formatOne(it) }

    fun formatOne(result: ImageSaver.SaveResult): String {
        val sb = StringBuilder()
        val title = result.originalName ?: "(unnamed)"
        sb.append(title)
        if (result.mimeType.isNotBlank()) {
            sb.append(" (").append(result.mimeType).append(")")
        }
        sb.append('\n')
        if (result.originalSize >= 0) {
            sb.append("Original: ").append(Format.humanSize(result.originalSize)).append('\n')
        }
        if (!result.success) {
            sb.append("FAILED: ").append(result.error ?: "unknown error")
            return sb.toString()
        }
        sb.append("Saved: ").append(result.savedName ?: "-")
            .append(" - ").append(Format.humanSize(result.savedSize)).append('\n')
        sb.append("Integrity: ").append(result.integrity.name)
        return sb.toString()
    }
}
