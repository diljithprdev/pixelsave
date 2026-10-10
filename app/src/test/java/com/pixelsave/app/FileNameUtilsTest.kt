package com.pixelsave.app

import com.pixelsave.app.util.FileNameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileNameUtilsTest {

    private val now = 1770000000000L

    @Test
    fun nullNameGeneratesTimestampFilename() {
        val name = FileNameUtils.buildDisplayName(null, "image/jpeg", now)
        assertTrue(name, Regex("""IMG_\d{8}_\d{6}_\d{3}\.jpg""").matches(name))
    }

    @Test
    fun blankNameGeneratesTimestampFilename() {
        val name = FileNameUtils.buildDisplayName("   ", "image/png", now)
        assertTrue(name, Regex("""IMG_\d{8}_\d{6}_\d{3}\.png""").matches(name))
    }

    @Test
    fun timestampWithoutKnownMimeHasNoExtension() {
        val name = FileNameUtils.buildDisplayName(null, "application/octet-stream", now)
        assertTrue(name, Regex("""IMG_\d{8}_\d{6}_\d{3}""").matches(name))
    }

    @Test
    fun missingExtensionIsAppendedFromMime() {
        assertEquals("photo.png", FileNameUtils.buildDisplayName("photo", "image/png", now))
    }

    @Test
    fun matchingExtensionIsPreserved() {
        assertEquals("photo.jpg", FileNameUtils.buildDisplayName("photo.jpg", "image/jpeg", now))
        assertEquals("photo.jpeg", FileNameUtils.buildDisplayName("photo.jpeg", "image/jpeg", now))
        assertEquals("holiday.PNG", FileNameUtils.buildDisplayName("holiday.PNG", "image/png", now))
    }

    @Test
    fun mismatchedExtensionIsNormalizedToMime() {
        assertEquals("photo.png", FileNameUtils.buildDisplayName("photo.jpg", "image/png", now))
    }

    @Test
    fun neverCreatesDuplicateExtension() {
        val name = FileNameUtils.buildDisplayName("vacation.jpg", "image/jpeg", now)
        assertEquals("vacation.jpg", name)
        val noExt = FileNameUtils.buildDisplayName("screenshot", "image/jpeg", now)
        assertEquals("screenshot.jpg", noExt)
        assertTrue(!name.endsWith(".jpg.jpg"))
    }

    @Test
    fun heicAndHeifExtensionsAreEquivalent() {
        assertEquals("a.heic", FileNameUtils.buildDisplayName("a.heif", "image/heic", now))
        assertEquals("a.heif", FileNameUtils.buildDisplayName("a.heic", "image/heif", now))
        assertEquals("b.HEIC", FileNameUtils.buildDisplayName("b.HEIC", "image/heic", now))
    }

    @Test
    fun pathSeparatorsAreSanitized() {
        assertEquals("a_b_c.png", FileNameUtils.buildDisplayName("a/b\\c.png", "image/png", now))
    }

    @Test
    fun unknownMimeKeepsOriginalFilename() {
        assertEquals(
            "archive.bin",
            FileNameUtils.buildDisplayName("archive.bin", "application/octet-stream", now)
        )
        assertEquals("noext", FileNameUtils.buildDisplayName("noext", "application/octet-stream", now))
    }

    @Test
    fun trailingDotReceivesExtension() {
        assertEquals("x.png", FileNameUtils.buildDisplayName("x.", "image/png", now))
    }

    @Test
    fun dotDotNameGeneratesTimestamp() {
        val name = FileNameUtils.buildDisplayName("..", "image/jpeg", now)
        assertTrue(name, name.startsWith("IMG_"))
    }

    @Test
    fun uniqueNameAppendsCounterOnCollision() {
        val dir = Files.createTempDirectory("pixelsave_test").toFile()
        try {
            File(dir, "a.jpg").writeBytes(byteArrayOf(1))
            File(dir, "a (1).jpg").writeBytes(byteArrayOf(1))
            assertEquals("a (2).jpg", FileNameUtils.uniqueName(dir, "a.jpg"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
