package com.pixelsave.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelsave.app.util.ShareIntents

@RunWith(AndroidJUnit4::class)
class ShareIntentTest {

    @Test
    fun extractsSingleStreamExtra() {
        val uri = Uri.parse("content://media/external/images/media/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        assertEquals(listOf(uri), ShareIntents.extractUris(intent))
    }

    @Test
    fun extractsMultipleStreamsAndDedupesClipData() {
        val u1 = Uri.parse("content://media/external/images/media/1")
        val u2 = Uri.parse("content://media/external/images/media/2")
        val u3 = Uri.parse("content://media/external/images/media/3")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(u1, u2))
        }
        val clip = android.content.ClipData.newRawUri("images", u1)
        clip.addItem(android.content.ClipData.Item(u3))
        intent.clipData = clip
        assertEquals(listOf(u1, u2, u3), ShareIntents.extractUris(intent))
    }

    @Test
    fun extractsFromClipDataOnly() {
        val u1 = Uri.parse("content://com.example/gallery/42")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = android.content.ClipData.newRawUri("image", u1)
        }
        assertEquals(listOf(u1), ShareIntents.extractUris(intent))
    }

    @Test
    fun ignoresNonStreamTextExtras() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/photo.jpg")
        }
        assertTrue(ShareIntents.extractUris(intent).isEmpty())
    }
}
