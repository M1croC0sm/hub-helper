package app.hubhelper

import java.math.BigDecimal

private val SickDayHours = BigDecimal(8)

fun sickDaysFromHours(hours: String): String = hours.toBigDecimalOrNull()
    ?.divide(SickDayHours)
    ?.stripTrailingZeros()
    ?.toPlainString()
    ?: ""

fun sickHoursFromDays(days: String): String = days.toBigDecimalOrNull()
    ?.multiply(SickDayHours)
    ?.stripTrailingZeros()
    ?.toPlainString()
    ?: ""

fun dayLabel(value: String): String = if (value == "1") "day" else "days"
