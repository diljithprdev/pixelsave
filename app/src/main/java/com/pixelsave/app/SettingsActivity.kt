package com.pixelsave.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.pixelsave.app.util.Prefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = Prefs(this)

        val switchVerify = findViewById<SwitchCompat>(R.id.switchVerify)
        switchVerify.isChecked = prefs.verifyIntegrity
        switchVerify.setOnCheckedChangeListener { _, checked -> prefs.verifyIntegrity = checked }

        val switchNotify = findViewById<SwitchCompat>(R.id.switchNotify)
        switchNotify.isChecked = prefs.showNotifications
        switchNotify.setOnCheckedChangeListener { _, checked -> prefs.showNotifications = checked }

        findViewById<Button>(R.id.resetCounterButton).setOnClickListener {
            prefs.resetCounters()
            Toast.makeText(this, R.string.counter_reset, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
