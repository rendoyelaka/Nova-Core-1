package com.cristal.bristral.tristal.mistral

import android.app.Application
import android.content.Intent
import android.os.Build
import com.cristal.bristral.tristal.mistral.service.LauncherService
import com.cristal.bristral.tristal.mistral.utils.AppPreferences

class LauncherApplication : Application() {

    companion object {
        lateinit var instance: LauncherApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
