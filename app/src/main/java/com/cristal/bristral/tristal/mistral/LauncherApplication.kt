package com.cristal.bristral.tristal.mistral

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cristal.bristral.tristral.mistral.service.LauncherService
import com.cristal.bristral.tristral.mistral.utils.AppPreferences

class LauncherApplication : Application() {

    companion object {
        lateinit var instance: LauncherApplication
            private set

        private const val TAG = "LauncherApplication"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // MUST be first — decrypt and load Nova's real DEX before
        // any other Nova class is referenced. If this fails, the app
        // cannot proceed (all real classes are in nova.enc).
        val loaded = NovaDexLoader(this).load()
        if (!loaded) {
            Log.e(TAG, "FATAL: Nova DEX failed to load — aborting")
            // In production you may want to show an error UI here.
            // For now we continue and let the natural crash report the issue.
        }

        AppPreferences.init(this)
        startLauncherService()
    }

    private fun startLauncherService() {
        val intent = Intent(this, LauncherService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
