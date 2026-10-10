package com.pixelsave.app.save

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.pixelsave.app.util.FileNameUtils
import com.pixelsave.app.util.MimeTypes
import com.pixelsave.app.util.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

class ImageSaver(private val context: Context, private val verifyIntegrity: Boolean = true) {

    enum class Integrity { VERIFIED, FAILED, SKIPPED }

    data class SaveResult(
        val success: Boolean,
        val originalName: String?,
        val mimeType: String,
        val originalSize: Long,
        val savedName: String?,
        val savedSize: Long,
        val sourceSha256: String?,
        val savedSha256: String?,
        val integrity: Integrity,
        val error: String? = null
    )

    private data class SourceMeta(
        val name: String?,
        val size: Long,
        val mime: String?,
        val dateTaken: Long?
    )

    private val resolver: ContentResolver
        get() = context.contentResolver

    suspend fun save(sourceUri: Uri, mimeTypeHint: String?): SaveResult = withContext(Dispatchers.IO) {
        var destUri: Uri? = null
        var legacyFile: File? = null
        var originalName: String? = null
        var originalSize = -1L
        var mime = "application/octet-stream"
        var displayName = ""
        var sourceHash: String? = null
        var savedHash: String? = null
        var integrity = Integrity.SKIPPED

        try {
            val meta = querySourceMeta(sourceUri)
            originalName = meta.name
            originalSize = meta.size
            mime = MimeTypes.resolveMimeType(meta.mime, mimeTypeHint, meta.name)
            displayName = FileNameUtils.buildDisplayName(meta.name, mime, System.currentTimeMillis())

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                meta.dateTaken?.let { put(MediaStore.Images.ImageColumns.DATE_TAKEN, it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            destUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore insertion failed")
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    LEGACY_FOLDER
                )
                if (!dir.isDirectory && !dir.mkdirs()) {
                    throw IOException("Could not create Pictures/$LEGACY_FOLDER folder")
                }
                val file = File(dir, FileNameUtils.uniqueName(dir, displayName))
                legacyFile = file
                values.put(MediaStore.MediaColumns.DATA, file.absolutePath)
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore insertion failed")
            }

            val digest = if (verifyIntegrity) MessageDigest.getInstance("SHA-256") else null
            var copied = 0L
            val input = resolver.openInputStream(sourceUri)
                ?: throw IOException("Cannot open the shared image")
            input.use { ins ->
                val output = resolver.openOutputStream(destUri)
                    ?: throw IOException("Cannot open the destination for writing")
                output.use { outs ->
                    copied = Streams.copy(
                        input = ins,
                        output = BufferedOutputStream(outs, Streams.BUFFER_SIZE),
                        digest = digest,
                        onBuffer = { ensureActive() }
                    )
                }
            }

            if (originalSize < 0) originalSize = copied
            var savedSize = copied

            if (digest != null) {
                sourceHash = Streams.toHex(digest.digest())
                val destInput = resolver.openInputStream(destUri)
                    ?: throw IOException("Cannot read back the saved file")
                val reread = destInput.use { ins -> Streams.sha256(ins) }
                savedHash = reread.first
                savedSize = reread.second
                integrity = if (savedHash == sourceHash && reread.second == copied) {
                    Integrity.VERIFIED
                } else {
                    Integrity.FAILED
                }
                if (integrity != Integrity.VERIFIED) {
                    throw IOException("Integrity verification failed: saved bytes differ from source")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finalize = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                val updated = resolver.update(destUri, finalize, null, null)
                if (updated <= 0) {
                    throw IOException("Could not finalize the saved image")
                }
            }

            val actualName = runCatching { queryDisplayName(destUri) }.getOrNull() ?: displayName
            SaveResult(
                success = true,
                originalName = originalName ?: displayName,
                mimeType = mime,
                originalSize = originalSize,
                savedName = actualName,
                savedSize = savedSize,
                sourceSha256 = sourceHash,
                savedSha256 = savedHash,
                integrity = integrity
            )
        } catch (e: CancellationException) {
            cleanup(destUri, legacyFile)
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to save $sourceUri", t)
            cleanup(destUri, legacyFile)
            SaveResult(
                success = false,
                originalName = originalName ?: displayName.ifBlank { null },
                mimeType = mime,
                originalSize = originalSize,
                savedName = null,
                savedSize = -1,
                sourceSha256 = sourceHash,
                savedSha256 = savedHash,
                integrity = integrity,
                error = friendlyError(t)
            )
        }
    }

    private fun querySourceMeta(uri: Uri): SourceMeta {
        val mime = try {
            resolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        var name: String? = null
        var size = -1L
        var dateTaken: Long? = null
        val projections = arrayOf(
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DATE_TAKEN_COLUMN),
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            arrayOf(OpenableColumns.DISPLAY_NAME)
        )
        for (projection in projections) {
            try {
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                        val dateIndex = cursor.getColumnIndex(DATE_TAKEN_COLUMN)
                        if (dateIndex >= 0 && !cursor.isNull(dateIndex)) {
                            dateTaken = cursor.getLong(dateIndex)
                        }
                    }
                }
                break
            } catch (e: Exception) {
                Log.d(TAG, "Query with projection failed, retrying narrower: ${e.message}")
            }
        }
        return SourceMeta(name, size, mime, dateTaken)
    }

    private fun queryDisplayName(uri: Uri): String? {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getString(index)
                    }
                }
            }
        return null
    }

    private fun cleanup(destUri: Uri?, legacyFile: File?) {
        try {
            if (destUri != null) {
                resolver.delete(destUri, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean up destination row", e)
        }
        try {
            if (legacyFile != null && legacyFile.exists()) {
                legacyFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean up destination file", e)
        }
    }

    private fun friendlyError(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            t is SecurityException -> "Permission denied: the shared URI is no longer accessible"
            t is FileNotFoundException -> "Source file not found, inaccessible, or permission expired"
            message.contains("No space", ignoreCase = true) ||
                message.contains("ENOSPC", ignoreCase = true) -> "Insufficient storage space"
            message.isNotBlank() -> message
            else -> t.javaClass.simpleName
        }
    }

    companion object {
        const val RELATIVE_PATH = "Pictures/PixelSave"
        private const val LEGACY_FOLDER = "PixelSave"
        private const val DATE_TAKEN_COLUMN = "datetaken"
        private const val TAG = "PixelSave"
    }
}
