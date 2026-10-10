package com.pixelsave.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixelsave.app.save.ImageSaver
import com.pixelsave.app.util.Streams
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageSaverTest {

    private data class SavedRow(
        val uri: Uri,
        val mimeType: String?,
        val relativePath: String?,
        val dataPath: String?,
        val pending: Int,
        val size: Long
    )

    private lateinit var context: Context
    private lateinit var workDir: File
    private val createdSourceUris = mutableListOf<Uri>()
    private val createdSavedNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "pixelsave_instrumented").apply {
            deleteRecursively()
            mkdirs()
        }
        if (Build.VERSION.SDK_INT in 26..28) {
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand(
                        "pm grant ${context.packageName} android.permission.WRITE_EXTERNAL_STORAGE"
                    )
                    .close()
            } catch (e: Exception) {
            }
        }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
        createdSourceUris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        createdSourceUris.clear()
        createdSavedNames.forEach { name ->
            runCatching { querySavedRow(name)?.let { context.contentResolver.delete(it.uri, null, null) } }
        }
        createdSavedNames.clear()
    }

    private fun sourceFile(name: String, bytes: ByteArray): File =
        File(workDir, name).also { it.writeBytes(bytes) }

    private fun fileUri(file: File): Uri = Uri.fromFile(file)

    private fun sha(bytes: ByteArray): String = Streams.sha256Hex(bytes)

    private fun save(file: File, hint: String?): ImageSaver.SaveResult =
        runBlocking { ImageSaver(context, true).save(fileUri(file), hint) }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun readSavedBytes(name: String): ByteArray? = querySavedRow(name)?.let { readBytes(it.uri) }

    private fun insertPendingSource(name: String, mime: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PixelSaveTestSource")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        createdSourceUris.add(uri)
        return uri
    }

    private fun querySavedRow(name: String): SavedRow? {
        val projection: Array<String>
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.IS_PENDING,
                MediaStore.MediaColumns.SIZE
            )
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND (" +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?)"
            args = arrayOf(name, "Pictures/PixelSave", "Pictures/PixelSave/")
        } else {
            projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE
            )
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.MediaColumns.DATA} LIKE ?"
            args = arrayOf(name, "%/Pictures/PixelSave/%")
        }
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                fun stringOf(column: String): String? {
                    val index = cursor.getColumnIndex(column)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                }
                fun longOf(column: String): Long {
                    val index = cursor.getColumnIndex(column)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
                }
                val id = longOf(MediaStore.MediaColumns._ID)
                return SavedRow(
                    uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    ),
                    mimeType = stringOf(MediaStore.MediaColumns.MIME_TYPE),
                    relativePath = stringOf(MediaStore.MediaColumns.RELATIVE_PATH),
                    dataPath = stringOf(MediaStore.MediaColumns.DATA),
                    pending = longOf(MediaStore.MediaColumns.IS_PENDING).toInt(),
                    size = longOf(MediaStore.MediaColumns.SIZE)
                )
            }
        }
        return null
    }

    private fun countPendingInDestination(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var count = 0
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.IS_PENDING}=1 AND (" +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?)",
            arrayOf("Pictures/PixelSave", "Pictures/PixelSave/"),
            null
        )?.use { cursor ->
            count = cursor.count
        }
        return count
    }

    @Test
    fun jpegSharedFromOtherApp_savedByteForByte() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val bytes = TestFixtures.jpegBytes(640, 480)
        val sourceUri = insertPendingSource("IMG_shared_from_app.jpg", "image/jpeg", bytes)

        val result = runBlocking { ImageSaver(context, true).save(sourceUri, "image/jpeg") }
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals("IMG_shared_from_app.jpg", result.savedName)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(bytes.size.toLong(), result.originalSize)
        assertEquals(bytes.size.toLong(), result.savedSize)
        assertEquals(sha(bytes), result.sourceSha256)
        assertEquals(result.sourceSha256, result.savedSha256)
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertArrayEquals(bytes, readSavedBytes(result.savedName!!))
    }

    @Test
    fun png_remainsPng() {
        val bytes = TestFixtures.pngBytes(320, 240)
        val file = sourceFile("test_image.png", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals("image/png", result.mimeType)
        assertTrue(result.savedName!!, result.savedName!!.endsWith(".png"))
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertArrayEquals(bytes, readSavedBytes(result.savedName!!))
        assertEquals("image/png", querySavedRow(result.savedName!!)?.mimeType)
    }

    @Test
    fun webp_remainsWebp() {
        val bytes = TestFixtures.webpBytes(320, 240)
        val file = sourceFile("test_image.webp", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals("image/webp", result.mimeType)
        assertTrue(result.savedName!!, result.savedName!!.endsWith(".webp"))
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertArrayEquals(bytes, readSavedBytes(result.savedName!!))
        assertEquals("image/webp", querySavedRow(result.savedName!!)?.mimeType)
    }

    @Test
    fun heic_remainsHeic() {
        val bytes = TestFixtures.heicStubBytes()
        val file = sourceFile("photo.heic", bytes)
        val result = save(file, "image/heic")
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals("image/heic", result.mimeType)
        assertTrue(result.savedName!!, result.savedName!!.endsWith(".heic"))
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertArrayEquals(bytes, readSavedBytes(result.savedName!!))
        assertEquals("image/heic", querySavedRow(result.savedName!!)?.mimeType)
    }

    @Test
    fun largeImage_isNotResized() {
        val bytes = TestFixtures.jpegBytes(4000, 3000)
        val file = sourceFile("large_4000x3000.jpg", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals(bytes.size.toLong(), result.savedSize)
        assertEquals(sha(bytes), result.savedSha256)

        val savedRow = querySavedRow(result.savedName!!)!!
        val savedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(savedRow.uri)!!.use {
            BitmapFactory.decodeStream(it, null, savedBounds)
        }
        val sourceBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sourceBounds)

        assertEquals(4000, sourceBounds.outWidth)
        assertEquals(3000, sourceBounds.outHeight)
        assertEquals(sourceBounds.outWidth, savedBounds.outWidth)
        assertEquals(sourceBounds.outHeight, savedBounds.outHeight)
    }

    @Test
    fun exifMetadata_remainsIntact() {
        val bytes = TestFixtures.jpegBytes(800, 600)
        val file = sourceFile("exif_test.jpg", bytes)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, "6")
            setAttribute(ExifInterface.TAG_MAKE, "PixelSaveTest")
            setAttribute(ExifInterface.TAG_MODEL, "UnitTestCam")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:07 15:30:12")
            saveAttributes()
        }
        val sourceBytes = file.readBytes()
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals(sha(sourceBytes), result.sourceSha256)
        assertEquals(result.sourceSha256, result.savedSha256)
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)

        val savedRow = querySavedRow(result.savedName!!)!!
        val savedExif = context.contentResolver.openInputStream(savedRow.uri)!!.use { ExifInterface(it) }
        assertEquals("6", savedExif.getAttribute(ExifInterface.TAG_ORIENTATION))
        assertEquals("PixelSaveTest", savedExif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("UnitTestCam", savedExif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("2026:10:07 15:30:12", savedExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun originalFileSizesAreUnchanged() {
        val bytes = TestFixtures.jpegBytes(1280, 720, quality = 100)
        val file = sourceFile("size_check.jpg", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        assertEquals(bytes.size.toLong(), result.originalSize)
        assertEquals(bytes.size.toLong(), result.savedSize)
        assertEquals(result.originalSize, result.savedSize)
        val row = querySavedRow(result.savedName!!)!!
        assertEquals(bytes.size.toLong(), row.size)
        assertEquals(bytes.size.toLong(), readBytes(row.uri).size.toLong())
    }

    @Test
    fun sha256OfSourceMatchesDestination() {
        val bytes = TestFixtures.jpegBytes(1024, 768)
        val file = sourceFile("hash_check.jpg", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        val expected = sha(bytes)
        assertEquals(expected, result.sourceSha256)
        assertEquals(expected, result.savedSha256)
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertEquals(expected, sha(readSavedBytes(result.savedName!!)!!))
    }

    @Test
    fun multipleImages_areAllSaved() {
        val f1 = sourceFile("multi_1.jpg", TestFixtures.jpegBytes(320, 240))
        val f2 = sourceFile("multi_2.png", TestFixtures.pngBytes(320, 240))
        val f3 = sourceFile("multi_3.webp", TestFixtures.webpBytes(320, 240))
        val files = listOf(f1, f2, f3)

        val results = mutableListOf<ImageSaver.SaveResult>()
        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        files.forEachIndexed { index, file ->
            results.add(save(file, null))
            progressUpdates.add((index + 1) to files.size)
        }
        results.forEach { createdSavedNames.add(it.savedName ?: "") }

        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
        assertTrue(results.all { it.integrity == ImageSaver.Integrity.VERIFIED })
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progressUpdates)
        assertNotEquals(results[0].savedName, results[1].savedName)
        assertNotNull(readSavedBytes("multi_1.jpg"))
        assertNotNull(readSavedBytes("multi_2.png"))
        assertNotNull(readSavedBytes("multi_3.webp"))
    }

    @Test
    fun savedImage_appearsInGalleryMediaStore() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val bytes = TestFixtures.jpegBytes(640, 480)
        val file = sourceFile("gallery_check.jpg", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        val row = querySavedRow(result.savedName!!)!!
        assertEquals(0, row.pending)

        context.contentResolver.openFileDescriptor(row.uri, "r")!!.use { pfd ->
            assertEquals(bytes.size.toLong(), pfd.statSize)
        }
        assertArrayEquals(bytes, readBytes(row.uri))
    }

    @Test
    fun savesUndecodableBytes_withoutBitmapDecoding() {
        val bytes = TestFixtures.randomBytes(256 * 1024)
        val file = sourceFile("not_really_a_jpeg.jpg", bytes)
        val result = save(file, "image/jpeg")
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(
            "A Bitmap-based pipeline could not save undecodable bytes: ${result.error}",
            result.success
        )
        assertEquals(ImageSaver.Integrity.VERIFIED, result.integrity)
        assertArrayEquals(bytes, readSavedBytes(result.savedName!!))
    }

    @Test
    fun usesScopedStorageMediaStoreRelativePaths() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val bytes = TestFixtures.jpegBytes(320, 240)
        val file = sourceFile("scoped_check.jpg", bytes)
        val result = save(file, null)
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        val row = querySavedRow(result.savedName!!)!!
        assertNotNull(row.relativePath)
        assertTrue(row.relativePath!!, row.relativePath!!.startsWith("Pictures/PixelSave"))
        assertNotNull(row.dataPath)
        assertTrue(row.dataPath!!, row.dataPath!!.contains("/Pictures/PixelSave/"))

        val manageGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        assertFalse("App must never hold MANAGE_EXTERNAL_STORAGE", manageGranted)
    }

    @Test
    fun duplicateFilenames_doNotOverwrite() {
        val bytes = TestFixtures.jpegBytes(320, 240)
        val first = save(sourceFile("dup_check.jpg", bytes), null)
        createdSavedNames.add(first.savedName ?: "")
        val second = save(sourceFile("dup_check.jpg", bytes), null)
        createdSavedNames.add(second.savedName ?: "")

        assertTrue(first.error ?: "first save failed", first.success)
        assertTrue(second.error ?: "second save failed", second.success)
        assertNotEquals(first.savedName, second.savedName)
        assertNotNull(readSavedBytes(first.savedName!!))
        assertNotNull(readSavedBytes(second.savedName!!))
    }

    @Test
    fun failedSave_cleansUpPendingDestination() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val file = sourceFile("will_be_deleted.jpg", TestFixtures.jpegBytes(100, 100))
        val uri = fileUri(file)
        assertTrue(file.delete())

        val result = runBlocking { ImageSaver(context, true).save(uri, "image/jpeg") }
        assertFalse(result.success)
        assertNotNull(result.error)
        assertEquals(0, countPendingInDestination())
    }

    @Test
    fun mimeAndMediaStoreColumnsAreCorrect() {
        val bytes = TestFixtures.pngBytes(200, 200)
        val file = sourceFile("column_check.png", bytes)
        val result = save(file, "image/png")
        createdSavedNames.add(result.savedName ?: "")

        assertTrue(result.error ?: "save failed", result.success)
        val row = querySavedRow(result.savedName!!)!!
        assertEquals("image/png", row.mimeType)
        assertEquals(bytes.size.toLong(), row.size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(0, row.pending)
        }
    }
}
