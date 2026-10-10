package com.pixelsave.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pixelsave.app.notify.Notifications
import com.pixelsave.app.save.ImageSaver
import com.pixelsave.app.util.Prefs
import com.pixelsave.app.util.ReportFormatter
import com.pixelsave.app.util.ShareIntents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var counterText: TextView
    private lateinit var resultsText: TextView
    private lateinit var progressRow: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var prefs: Prefs
    private var shareJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        counterText = findViewById(R.id.counterText)
        resultsText = findViewById(R.id.resultsText)
        progressRow = findViewById(R.id.progressRow)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestRuntimePermissions()
        renderState()

        if (savedInstanceState == null) {
            handleShareIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val uris = ShareIntents.extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.no_images_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasLegacyStoragePermission()) {
            requestRuntimePermissions()
            Toast.makeText(this, R.string.storage_permission_needed, Toast.LENGTH_LONG).show()
            return
        }
        processShare(uris, intent.type)
    }

    private fun processShare(uris: List<Uri>, mimeHint: String?) {
        shareJob?.cancel()
        shareJob = lifecycleScope.launch {
            val saver = ImageSaver(applicationContext, prefs.verifyIntegrity)
            val total = uris.size
            val results = mutableListOf<ImageSaver.SaveResult>()
            var savedCount = 0
            try {
                showProgress(true, 0, total)
                for ((index, uri) in uris.withIndex()) {
                    showProgress(true, index + 1, total)
                    val result = saver.save(uri, mimeHint)
                    results.add(result)
                    if (result.success) {
                        savedCount++
                        prefs.recordSaved(result.savedName)
                    }
                    prefs.lastReport = ReportFormatter.format(results)
                    resultsText.text = prefs.lastReport
                    renderCounter()
                }
                if (savedCount > 0) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.saved_toast, savedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (prefs.showNotifications) {
                        Notifications.showSaved(
                            applicationContext,
                            savedCount,
                            results.lastOrNull { it.success }?.savedName
                        )
                    }
                } else {
                    Toast.makeText(this@MainActivity, R.string.save_failed_toast, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Toast.makeText(
                    this@MainActivity,
                    t.message ?: t.javaClass.simpleName,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showProgress(false, 0, total)
            }
        }
    }

    private fun showProgress(visible: Boolean, current: Int, total: Int) {
        progressRow.visibility = if (visible) View.VISIBLE else View.GONE
        progressBar.max = total
        progressBar.progress = current
        if (current in 1..total) {
            progressText.text = getString(R.string.progress_saving, current, total)
        }
    }

    private fun renderState() {
        renderCounter()
        val report = prefs.lastReport
        resultsText.text = if (report.isBlank()) getString(R.string.no_saves_yet) else report
    }

    private fun renderCounter() {
        counterText.text = getString(R.string.images_saved, prefs.savedCount)
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (!hasLegacyStoragePermission()) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
            }
        }
    }

    private fun hasLegacyStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
        private const val REQUEST_STORAGE = 101
    }
}
