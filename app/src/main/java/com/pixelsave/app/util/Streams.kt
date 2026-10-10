package com.pixelsave.app.util

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object Streams {

    const val BUFFER_SIZE = 64 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        digest: MessageDigest? = null,
        onBuffer: (() -> Unit)? = null
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            digest?.update(buffer, 0, read)
            total += read
            onBuffer?.invoke()
        }
        output.flush()
        return total
    }

    fun sha256(input: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
            total += read
        }
        return toHex(digest.digest()) to total
    }

    fun sha256Hex(bytes: ByteArray): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt()
            sb.append(Character.forDigit((v shr 4) and 0xF, 16))
            sb.append(Character.forDigit(v and 0xF, 16))
        }
        return sb.toString()
    }
}
