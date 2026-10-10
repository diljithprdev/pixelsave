package com.pixelsave.app

import com.pixelsave.app.util.Streams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class StreamsTest {

    @Test
    fun copyPreservesEveryByteAcrossBufferBoundaries() {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")
        val count = Streams.copy(ByteArrayInputStream(data), output, digest)
        assertArrayEquals(data, output.toByteArray())
        assertEquals(data.size.toLong(), count)
        assertEquals(Streams.sha256Hex(data), Streams.toHex(digest.digest()))
    }

    @Test
    fun copyHandlesEmptyInput() {
        val output = ByteArrayOutputStream()
        val count = Streams.copy(ByteArrayInputStream(ByteArray(0)), output, null)
        assertEquals(0L, count)
        assertEquals(0, output.size())
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Streams.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun sha256StreamEqualsByteArrayHash() {
        val data = ByteArray(200_000) { (it % 137).toByte() }
        val (hex, count) = Streams.sha256(ByteArrayInputStream(data))
        assertEquals(Streams.sha256Hex(data), hex)
        assertEquals(data.size.toLong(), count)
    }

    @Test
    fun onBufferCallbackFiresForEveryChunk() {
        val callbacks = AtomicInteger()
        Streams.copy(
            ByteArrayInputStream(ByteArray(200_000)),
            ByteArrayOutputStream(),
            null,
            onBuffer = { callbacks.incrementAndGet() }
        )
        assertEquals(4, callbacks.get())
    }
}
