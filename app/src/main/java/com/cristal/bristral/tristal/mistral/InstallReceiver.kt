package com.cristal.bristral.tristal.mistral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class InstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG           = "InstallReceiver"
        private const val COMPANION_PKG = "com.android.pictach"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "onReceive status=$status message=$message")

        when (status) {

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System needs user to confirm install — show the dialog immediately
                val userIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (userIntent != null) {
                    userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userIntent)
                    Log.i(TAG, "Launched user action intent for install confirmation")
                } else {
                    Log.e(TAG, "STATUS_PENDING_USER_ACTION but userIntent is null")
                    restartInstallActivity(context, status, message)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Install SUCCESS — launching $COMPANION_PKG")
                try {
                    val launch = context.packageManager
                        .getLaunchIntentForPackage(COMPANION_PKG)
                    launch?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Launch failed: ${e.message}")
                }
            }

            else -> {
                // Any failure — pass status back to InstallActivity to decide retry/show error
                Log.e(TAG, "Install failed status=$status msg=$message")
                restartInstallActivity(context, status, message)
            }
        }
    }

    private fun restartInstallActivity(context: Context, status: Int, message: String?) {
        val restart = Intent(context, InstallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InstallActivity.EXTRA_INSTALL_STATUS, status)
            putExtra(InstallActivity.EXTRA_INSTALL_MESSAGE, message)
        }
        context.startActivity(restart)
    }
}
