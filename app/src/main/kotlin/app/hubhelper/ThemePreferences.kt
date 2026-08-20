package app.hubhelper

import android.content.Context

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = preferences.getBoolean("dark_mode", true)
        set(value) { preferences.edit().putBoolean("dark_mode", value).apply() }
}
