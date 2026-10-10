package com.pixelsave.app.util

object MimeTypes {

    private val extensionToMime = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "jpe" to "image/jpeg",
        "jfif" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "dng" to "image/x-adobe-dng",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon"
    )

    private val mimeToExtension = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/heic" to "heic",
        "image/heif" to "heif",
        "image/avif" to "avif",
        "image/gif" to "gif",
        "image/bmp" to "bmp",
        "image/x-adobe-dng" to "dng",
        "image/tiff" to "tiff",
        "image/svg+xml" to "svg",
        "image/x-icon" to "ico"
    )

    fun fromExtension(extension: String?): String? =
        extension?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }?.let { extensionToMime[it] }

    fun extensionFor(mimeType: String?): String? =
        mimeType?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }?.let { mimeToExtension[it] }

    fun isConcrete(mimeType: String?): Boolean =
        !mimeType.isNullOrBlank() && mimeType != "*/*" && !mimeType.endsWith("/*")

    fun resolveMimeType(resolverType: String?, intentHint: String?, fileName: String?): String {
        if (isConcrete(resolverType)) return resolverType!!.lowercase()
        if (isConcrete(intentHint)) return intentHint!!.lowercase()
        val fromName = fromExtension(fileName?.substringAfterLast('.', ""))
        if (fromName != null) return fromName
        return "application/octet-stream"
    }
}
