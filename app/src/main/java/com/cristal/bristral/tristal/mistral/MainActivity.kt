package com.cristal.bristral.tristal.mistral

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var homeRunnable: Runnable

    companion object {
        // Set to true by AppDetailActivity before triggering uninstall
        // Prevents onResume() from redirecting and killing the uninstall popup
        var isUninstalling = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goToDefaultHome()
    }

    override fun onResume() {
        super.onResume()
        // CRITICAL: Don't redirect when uninstall popup is showing
        if (isUninstalling) {
            isUninstalling = false
            return
        }
        // If companion is already installed and Nova resumes as home,
        // it means companion fired ACTION_DELETE (system uninstall dialog
        // is about to appear in its own task). Set flag so SecondActivity
        // does not relaunch companion and dismiss the dialog.
        if (isCompanionInstalled()) {
            isUninstalling = true
        }
        if (isDefaultHome()) {
            goToSecondActivity()
        } else {
            goToDefaultHome()
        }
    }

    private fun isCompanionInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.android.pictach", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressed() {
        if (isDefaultHome()) {
            goToSecondActivity()
        } else {
            goToDefaultHome()
        }
    }

    private fun goToDefaultHome() {
        if (isDefaultHome()) {
            goToSecondActivity()
            return
        }
        Toast.makeText(
            this,
            "Please set this app as your default home launcher",
            Toast.LENGTH_LONG
        ).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent("android.settings.HOME_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS")
                intent.data = Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        homeRunnable = Runnable {
            if (isDefaultHome()) {
                goToSecondActivity()
            } else {
                handler.postDelayed(homeRunnable, 1000)
            }
        }
        handler.postDelayed(homeRunnable, 1000)
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return false
        return info.activityInfo?.packageName == packageName
    }

    private fun goToSecondActivity() {
        handler.removeCallbacksAndMessages(null)
        val intent = Intent(this, SecondActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
