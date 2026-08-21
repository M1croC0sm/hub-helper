package app.hubhelper

import android.content.Context

internal fun ThemeMode.resolveDarkMode(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var theme: HubTheme
        get() = preferences.getString("theme", null)
            ?.let { saved -> HubTheme.entries.firstOrNull { it.name == saved } }
            ?: HubTheme.INDUSTRIAL
        set(value) { preferences.edit().putString("theme", value.name).apply() }

    var mode: ThemeMode
        get() = preferences.getString("theme_mode", null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: if (preferences.getBoolean("dark_mode", true)) ThemeMode.DARK else ThemeMode.LIGHT
        set(value) { preferences.edit().putString("theme_mode", value.name).apply() }

    @Deprecated("Use mode; retained to migrate existing installations")
    var darkMode: Boolean
        get() = mode == ThemeMode.DARK
        set(value) { mode = if (value) ThemeMode.DARK else ThemeMode.LIGHT }
}
