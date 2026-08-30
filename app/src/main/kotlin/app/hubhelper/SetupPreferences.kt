package app.hubhelper

import android.content.Context
import java.time.LocalDate

data class SetupData(
    val ptoBalanceHours: String = "",
    val sickBalanceHours: String = "",
    val currentAttendancePoints: String = "",
    val attendanceOpeningRemainder: String = "",
    val shiftPreset: String? = null,
    val pointsSheetUri: String? = null,
    val hireDate: String = "",
    val balancesAsOfDate: String = LocalDate.now().toString(),
    val callInsRemaining: String = "",
    val callInsBalanceYear: String = "",
    val birthdayMonth: String = "",
    val floatingHolidayAllowance: String = "",
)

class SetupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("initial_setup", Context.MODE_PRIVATE)

    val isComplete: Boolean
        get() = preferences.getBoolean(KEY_COMPLETE, false)

    fun load(): SetupData {
        val currentPoints = preferences.getString(KEY_CURRENT_POINTS, "").orEmpty()
        val storedFloatingAllowance = preferences.getString(KEY_FLOATING_ALLOWANCE, null)
        return SetupData(
        ptoBalanceHours = preferences.getString(KEY_PTO, "").orEmpty(),
        sickBalanceHours = preferences.getString(KEY_SICK, "").orEmpty(),
        currentAttendancePoints = currentPoints,
        attendanceOpeningRemainder = if (preferences.contains(KEY_OPENING_REMAINDER))
            preferences.getString(KEY_OPENING_REMAINDER, "").orEmpty() else currentPoints,
        shiftPreset = preferences.getString(KEY_SHIFT, null),
        pointsSheetUri = preferences.getString(KEY_POINTS_SHEET, null),
        hireDate = preferences.getString(KEY_HIRE_DATE, "").orEmpty(),
        balancesAsOfDate = preferences.getString(KEY_BALANCES_AS_OF, LocalDate.now().toString()).orEmpty(),
        callInsRemaining = preferences.getString(KEY_CALL_INS_REMAINING, "").orEmpty(),
        callInsBalanceYear = preferences.getString(KEY_CALL_INS_YEAR, "").orEmpty(),
        birthdayMonth = preferences.getString(KEY_BIRTHDAY_MONTH, "").orEmpty(),
        floatingHolidayAllowance = storedFloatingAllowance ?: if (isComplete) "2" else "",
        )
    }

    fun save(data: SetupData) {
        preferences.edit()
            .putString(KEY_PTO, data.ptoBalanceHours)
            .putString(KEY_SICK, data.sickBalanceHours)
            .putString(KEY_CURRENT_POINTS, data.currentAttendancePoints)
            .putString(KEY_OPENING_REMAINDER, data.attendanceOpeningRemainder)
            .putString(KEY_SHIFT, data.shiftPreset)
            .putString(KEY_POINTS_SHEET, data.pointsSheetUri)
            .putString(KEY_HIRE_DATE, data.hireDate)
            .putString(KEY_BALANCES_AS_OF, data.balancesAsOfDate)
            .putString(KEY_CALL_INS_REMAINING, data.callInsRemaining)
            .putString(KEY_CALL_INS_YEAR, data.callInsBalanceYear)
            .putString(KEY_BIRTHDAY_MONTH, data.birthdayMonth)
            .putString(KEY_FLOATING_ALLOWANCE, data.floatingHolidayAllowance)
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }

    fun needsPointsSheetImport(uri: String): Boolean =
        preferences.getString(KEY_IMPORTED_POINTS_SHEET, null) != uri

    fun markPointsSheetImported(uri: String) {
        preferences.edit().putString(KEY_IMPORTED_POINTS_SHEET, uri).apply()
    }

    private companion object {
        const val KEY_COMPLETE = "complete"
        const val KEY_PTO = "pto_balance_hours"
        const val KEY_SICK = "sick_balance_hours"
        const val KEY_CURRENT_POINTS = "current_attendance_points"
        const val KEY_OPENING_REMAINDER = "attendance_opening_remainder"
        const val KEY_SHIFT = "shift_preset"
        const val KEY_POINTS_SHEET = "points_sheet_uri"
        const val KEY_IMPORTED_POINTS_SHEET = "imported_points_sheet_uri"
        const val KEY_HIRE_DATE = "hire_date"
        const val KEY_BALANCES_AS_OF = "balances_as_of_date"
        const val KEY_CALL_INS_REMAINING = "call_ins_remaining"
        const val KEY_CALL_INS_YEAR = "call_ins_balance_year"
        const val KEY_BIRTHDAY_MONTH = "birthday_month"
        const val KEY_FLOATING_ALLOWANCE = "floating_holiday_allowance"
    }
}
