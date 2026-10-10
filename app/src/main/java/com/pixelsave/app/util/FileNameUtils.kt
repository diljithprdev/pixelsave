package com.pixelsave.app.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNameUtils {

    private val jpegFamily = setOf("jpg", "jpeg", "jpe", "jfif")
    private val heifFamily = setOf("heic", "heif")
    private val tiffFamily = setOf("tif", "tiff")

    fun buildDisplayName(rawName: String?, mimeType: String?, nowMillis: Long): String {
        var name = rawName?.trim().orEmpty()
        name = name.replace('/', '_').replace('\\', '_')
        while (name.contains("..")) {
            name = name.replace("..", ".")
        }
        name = name.trim()
        if (name.isEmpty() || name == "." || name == "..") {
            return timestampName(nowMillis, mimeType)
        }
        val targetExt = MimeTypes.extensionFor(mimeType) ?: return name
        val currentExt = name.substringAfterLast('.', "")
        return when {
            '.' !in name -> "$name.$targetExt"
            currentExt.isEmpty() -> "$name$targetExt"
            extensionsEquivalent(currentExt, targetExt) -> name
            else -> name.dropLast(currentExt.length) + targetExt
        }
    }

    fun timestampName(nowMillis: Long, mimeType: String?): String {
        val base = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(nowMillis))
        val ext = MimeTypes.extensionFor(mimeType) ?: return base
        return "$base.$ext"
    }

    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (File(dir, "$base ($index)$ext").exists()) {
            index++
        }
        return "$base ($index)$ext"
    }

    private fun extensionsEquivalent(a: String, b: String): Boolean {
        val la = a.lowercase()
        val lb = b.lowercase()
        return la == lb ||
            (la in jpegFamily && lb in jpegFamily) ||
            (la in heifFamily && lb in heifFamily) ||
            (la in tiffFamily && lb in tiffFamily)
    }
}
