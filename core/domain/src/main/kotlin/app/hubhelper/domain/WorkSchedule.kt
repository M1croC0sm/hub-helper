package app.hubhelper.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ShiftPreset { FIRST, SECOND }

data class ScheduledShift(
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val overtime: Boolean,
)

class WorkScheduleCalculator {
    fun shift(date: LocalDate, preset: ShiftPreset, overtimeActive: Boolean = false): ScheduledShift? =
        when (preset) {
            ShiftPreset.FIRST -> firstShift(date, overtimeActive)
            ShiftPreset.SECOND -> secondShift(date, overtimeActive)
        }

    private fun firstShift(date: LocalDate, overtime: Boolean): ScheduledShift? {
        val times = when {
            date.dayOfWeek == DayOfWeek.SATURDAY && overtime -> LocalTime.of(5, 0) to LocalTime.of(11, 0)
            date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY && overtime -> LocalTime.of(5, 0) to LocalTime.of(14, 30)
            date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY -> LocalTime.of(6, 0) to LocalTime.of(14, 30)
            else -> return null
        }
        return ScheduledShift(date.atTime(times.first), date.atTime(times.second), overtime)
    }

    private fun secondShift(date: LocalDate, overtime: Boolean): ScheduledShift? {
        if (date.dayOfWeek == DayOfWeek.FRIDAY && overtime) {
            return ScheduledShift(date.atTime(14, 15), date.atTime(20, 15), true)
        }
        if (date.dayOfWeek !in DayOfWeek.MONDAY..DayOfWeek.THURSDAY) return null
        // Other weekday overtime terms are not known, so the regular schedule remains authoritative.
        return ScheduledShift(date.atTime(14, 15), date.plusDays(1).atTime(0, 45), false)
    }
}

