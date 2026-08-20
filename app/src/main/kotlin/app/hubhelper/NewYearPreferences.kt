package app.hubhelper

import android.content.Context

class NewYearPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("new_year_review", Context.MODE_PRIVATE)

    fun lastAcknowledgedYear(defaultYear: Int): Int =
        preferences.getInt("last_acknowledged_year", defaultYear)

    fun acknowledge(year: Int) {
        preferences.edit().putInt("last_acknowledged_year", year).apply()
    }
}
