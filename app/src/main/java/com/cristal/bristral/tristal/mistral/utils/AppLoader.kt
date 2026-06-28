package com.cristal.bristral.tristal.mistral.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.cristal.bristral.tristal.mistral.model.AppInfo

object AppLoader {

    fun getAllInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val apps = mutableListOf<AppInfo>()
        for (info in resolveInfoList) {
            val pkgName = info.activityInfo.packageName
            if (pkgName == context.packageName) continue
            try {
                apps.add(AppInfo(
                    name = info.loadLabel(pm).toString(),
                    packageName = pkgName,
                    icon = info.loadIcon(pm),
                    isSystemApp = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    lastUpdateTime = pm.getPackageInfo(pkgName, 0).lastUpdateTime
                ))
            } catch (e: Exception) { }
        }
        return apps.sorted()
    }

    fun getDefaultApps(context: Context): List<AppInfo> {
        val all = getAllInstalledApps(context)
        val defaults = setOf(
            "com.android.dialer","com.google.android.dialer",
            "com.android.contacts","com.google.android.contacts",
            "com.google.android.gm","com.android.mms",
            "com.google.android.apps.messaging","com.android.chrome",
            "com.google.android.youtube","com.android.settings"
        )
        val filtered = all.filter { it.packageName in defaults }
        return if (filtered.size >= 4) filtered.take(8) else all.take(8)
    }

    fun getAppsByPackages(context: Context, packages: Set<String>): List<AppInfo> {
        val pm = context.packageManager
        return packages.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                AppInfo(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = pkg,
                    icon = pm.getApplicationIcon(pkg),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (e: Exception) { null }
        }.sorted()
    }
}
