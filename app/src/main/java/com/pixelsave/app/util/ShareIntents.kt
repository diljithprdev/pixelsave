package com.pixelsave.app.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build

object ShareIntents {

    fun extractUris(intent: Intent): List<Uri> {
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> uriExtra(intent, Intent.EXTRA_STREAM)?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> uriListExtra(intent, Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val uri = clip.getItemAt(i)?.uri
                if (uri != null) {
                    uris.add(uri)
                }
            }
        }
        val data = intent.data
        if (data != null &&
            (data.scheme == ContentResolver.SCHEME_CONTENT || data.scheme == ContentResolver.SCHEME_FILE)
        ) {
            uris.add(data)
        }
        return uris.toList()
    }

    @Suppress("DEPRECATION")
    private fun uriExtra(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }

    @Suppress("DEPRECATION")
    private fun uriListExtra(intent: Intent, key: String): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(key)
        }
}
