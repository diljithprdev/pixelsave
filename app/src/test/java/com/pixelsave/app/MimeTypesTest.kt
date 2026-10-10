package com.pixelsave.app

import com.pixelsave.app.util.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MimeTypesTest {

    @Test
    fun resolverTypeWins() {
        assertEquals(
            "image/png",
            MimeTypes.resolveMimeType("image/png", "image/jpeg", "a.jpg")
        )
    }

    @Test
    fun genericResolverTypeIsSkipped() {
        assertEquals(
            "image/webp",
            MimeTypes.resolveMimeType("image/*", null, "a.webp")
        )
    }

    @Test
    fun concreteHintIsUsed() {
        assertEquals(
            "image/heic",
            MimeTypes.resolveMimeType(null, "image/heic", "a.jpg")
        )
    }

    @Test
    fun fileNameExtensionFallback() {
        assertEquals(
            "image/png",
            MimeTypes.resolveMimeType(null, "image/*", "shot.png")
        )
    }

    @Test
    fun unknownFallsBackToOctetStream() {
        assertEquals(
            "application/octet-stream",
            MimeTypes.resolveMimeType(null, "image/*", "file")
        )
        assertEquals(
            "application/octet-stream",
            MimeTypes.resolveMimeType(null, null, null)
        )
    }

    @Test
    fun mimeAndExtensionMappings() {
        assertEquals("jpg", MimeTypes.extensionFor("image/jpeg"))
        assertEquals("png", MimeTypes.extensionFor("image/png"))
        assertEquals("webp", MimeTypes.extensionFor("image/webp"))
        assertEquals("heic", MimeTypes.extensionFor("image/heic"))
        assertEquals("heif", MimeTypes.extensionFor("image/heif"))
        assertEquals("image/jpeg", MimeTypes.fromExtension("JPG"))
        assertEquals("image/heic", MimeTypes.fromExtension("heic"))
        assertNull(MimeTypes.extensionFor("application/octet-stream"))
        assertNull(MimeTypes.fromExtension(""))
    }

    @Test
    fun concreteDetection() {
        assertEquals(true, MimeTypes.isConcrete("image/jpeg"))
        assertEquals(false, MimeTypes.isConcrete("image/*"))
        assertEquals(false, MimeTypes.isConcrete("*/*"))
        assertEquals(false, MimeTypes.isConcrete(null))
        assertEquals(false, MimeTypes.isConcrete(""))
    }
}
