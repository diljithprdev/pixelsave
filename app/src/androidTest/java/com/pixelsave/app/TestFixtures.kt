package com.pixelsave.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import java.io.ByteArrayOutputStream
import java.util.Random

object TestFixtures {

    fun jpegBytes(width: Int, height: Int, quality: Int = 95): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawPattern(bitmap)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawPattern(bitmap)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    fun webpBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawPattern(bitmap)
            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 90, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    fun heicStubBytes(): ByteArray {
        val bytes = ByteArray(128)
        bytes[3] = 128
        "ftyp".toByteArray().copyInto(bytes, 4)
        "heic".toByteArray().copyInto(bytes, 8)
        val rnd = Random(11)
        for (i in 12 until bytes.size) {
            bytes[i] = rnd.nextInt(256).toByte()
        }
        return bytes
    }

    fun randomBytes(size: Int, seed: Long = 42L): ByteArray =
        ByteArray(size).also { Random(seed).nextBytes(it) }

    private fun drawPattern(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 144, 255))
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(255, 165, 0)
        }
        val w = bitmap.width
        val h = bitmap.height
        canvas.drawCircle(w * 0.25f, h * 0.25f, minOf(w, h) / 3f, paint)
        val rnd = Random(7)
        repeat(150) {
            paint.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            canvas.drawRect(
                (rnd.nextInt(w)).toFloat(),
                (rnd.nextInt(h)).toFloat(),
                (rnd.nextInt(w)).toFloat(),
                (rnd.nextInt(h)).toFloat(),
                paint
            )
        }
    }
}
