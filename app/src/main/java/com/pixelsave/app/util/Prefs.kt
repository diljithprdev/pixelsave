package com.pixelsave.app.util

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("pixelsave_prefs", Context.MODE_PRIVATE)

    var savedCount: Int
        get() = sp.getInt(KEY_COUNT, 0)
        set(value) {
            sp.edit().putInt(KEY_COUNT, value).apply()
        }

    var lastReport: String
        get() = sp.getString(KEY_REPORT, "") ?: ""
        set(value) {
            sp.edit().putString(KEY_REPORT, value).apply()
        }

    var lastSavedName: String?
        get() = sp.getString(KEY_LAST_NAME, null)
        set(value) {
            sp.edit().putString(KEY_LAST_NAME, value).apply()
        }

    var verifyIntegrity: Boolean
        get() = sp.getBoolean(KEY_VERIFY, true)
        set(value) {
            sp.edit().putBoolean(KEY_VERIFY, value).apply()
        }

    var showNotifications: Boolean
        get() = sp.getBoolean(KEY_NOTIFY, true)
        set(value) {
            sp.edit().putBoolean(KEY_NOTIFY, value).apply()
        }

    fun recordSaved(savedName: String?) {
        savedCount = savedCount + 1
        if (savedName != null) {
            lastSavedName = savedName
        }
    }

    fun resetCounters() {
        savedCount = 0
        lastSavedName = null
        lastReport = ""
    }

    private companion object {
        const val KEY_COUNT = "saved_count"
        const val KEY_REPORT = "last_report"
        const val KEY_LAST_NAME = "last_saved_name"
        const val KEY_VERIFY = "verify_integrity"
        const val KEY_NOTIFY = "show_notifications"
    }
}
