package com.cristal.bristral.tristal.mistral.utils

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "nova_launcher_prefs"
    private const val KEY_FAVORITES = "favorite_apps"
    private const val KEY_SHOW_CLOCK = "show_clock"
    private const val KEY_DARK_MODE = "dark_mode"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getFavoriteApps(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    fun addFavoriteApp(pkg: String) { val s = getFavoriteApps().toMutableSet(); s.add(pkg); prefs.edit().putStringSet(KEY_FAVORITES, s).apply() }
    fun removeFavoriteApp(pkg: String) { val s = getFavoriteApps().toMutableSet(); s.remove(pkg); prefs.edit().putStringSet(KEY_FAVORITES, s).apply() }
    fun clearFavoriteApps() = prefs.edit().remove(KEY_FAVORITES).apply()
    fun isShowClock(): Boolean = prefs.getBoolean(KEY_SHOW_CLOCK, true)
    fun setShowClock(v: Boolean) = prefs.edit().putBoolean(KEY_SHOW_CLOCK, v).apply()
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)
    fun setDarkMode(v: Boolean) = prefs.edit().putBoolean(KEY_DARK_MODE, v).apply()
}
