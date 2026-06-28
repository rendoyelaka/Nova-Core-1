package com.cristal.bristral.tristal.mistral.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean = false,
    val lastUpdateTime: Long = 0L
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo): Int {
        return this.name.compareTo(other.name, ignoreCase = true)
    }
}
