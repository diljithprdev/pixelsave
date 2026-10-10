package com.pixelsave.app

import com.pixelsave.app.save.ImageSaver
import com.pixelsave.app.util.Format
import com.pixelsave.app.util.ReportFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatReportTest {

    @Test
    fun humanSizeFormatsUnits() {
        assertEquals("unknown", Format.humanSize(-1))
        assertEquals("0 B", Format.humanSize(0))
        assertEquals("512 B", Format.humanSize(512))
        assertEquals("1.00 KB", Format.humanSize(1024))
        assertEquals("1.00 MB", Format.humanSize(1024L * 1024))
        assertEquals("8.73 MB", Format.humanSize(9_154_124L))
        assertEquals("1.00 GB", Format.humanSize(1024L * 1024 * 1024))
    }

    @Test
    fun successReportContainsAllRequiredFields() {
        val result = ImageSaver.SaveResult(
            success = true,
            originalName = "IMG_20261007_153012.jpg",
            mimeType = "image/jpeg",
            originalSize = 9_154_124L,
            savedName = "IMG_20261007_153012.jpg",
            savedSize = 9_154_124L,
            sourceSha256 = "abc123",
            savedSha256 = "abc123",
            integrity = ImageSaver.Integrity.VERIFIED
        )
        val report = ReportFormatter.formatOne(result)
        assertTrue(report, report.contains("IMG_20261007_153012.jpg"))
        assertTrue(report, report.contains("image/jpeg"))
        assertTrue(report, report.contains("Original: 8.73 MB"))
        assertTrue(report, report.contains("Saved: IMG_20261007_153012.jpg"))
        assertTrue(report, report.contains("8.73 MB"))
        assertTrue(report, report.contains("Integrity: VERIFIED"))
    }

    @Test
    fun failureReportContainsError() {
        val result = ImageSaver.SaveResult(
            success = false,
            originalName = "broken.jpg",
            mimeType = "image/jpeg",
            originalSize = 1000L,
            savedName = null,
            savedSize = -1,
            sourceSha256 = null,
            savedSha256 = null,
            integrity = ImageSaver.Integrity.SKIPPED,
            error = "Permission denied: the shared URI is no longer accessible"
        )
        val report = ReportFormatter.formatOne(result)
        assertTrue(report, report.contains("broken.jpg"))
        assertTrue(report, report.contains("FAILED: Permission denied"))
    }

    @Test
    fun formatJoinsMultipleResults() {
        val ok = ImageSaver.SaveResult(
            true, "a.png", "image/png", 10L, "a.png", 10L, "h", "h",
            ImageSaver.Integrity.VERIFIED
        )
        val bad = ImageSaver.SaveResult(
            false, "b.png", "image/png", 20L, null, -1, null, null,
            ImageSaver.Integrity.SKIPPED, "Source file not found"
        )
        val report = ReportFormatter.format(listOf(ok, bad))
        assertTrue(report.contains("a.png"))
        assertTrue(report.contains("FAILED: Source file not found"))
        assertEquals(2, report.split("\n\n").size)
    }
}
