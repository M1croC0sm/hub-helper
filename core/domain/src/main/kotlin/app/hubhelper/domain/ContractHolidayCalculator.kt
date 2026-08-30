package app.hubhelper.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** Holidays named in CBA Article XIII, excluding the roving and birthday holidays. */
object ContractHolidayCalculator {
    fun forYear(year: Int, secondShift: Boolean): List<ContractHoliday> {
        val fixed = listOf(
            LocalDate.of(year, Month.JANUARY, 1) to "New Year's Day",
            goodFriday(year) to "Good Friday",
            LocalDate.of(year, Month.MAY, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)) to "Memorial Day",
            LocalDate.of(year, Month.JULY, 4) to "Independence Day",
            LocalDate.of(year, Month.SEPTEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)) to "Labor Day",
            LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)) to "Thanksgiving Day",
            LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)).plusDays(1) to "Friday after Thanksgiving",
            lastWorkingDayBeforeChristmas(year) to "Christmas",
            LocalDate.of(year, Month.DECEMBER, 25) to "Christmas Day",
        )
        return fixed.map { (date, name) ->
            ContractHoliday(observedDate(date, secondShift), name)
        }.distinctBy { it.date to it.name }
            .sortedBy { it.date }
    }

    private fun observedDate(date: LocalDate, secondShift: Boolean): LocalDate = when {
        secondShift && date.dayOfWeek == DayOfWeek.FRIDAY -> date.minusDays(1)
        date.dayOfWeek == DayOfWeek.SATURDAY -> date.minusDays(1)
        date.dayOfWeek == DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun lastWorkingDayBeforeChristmas(year: Int): LocalDate {
        var date = LocalDate.of(year, Month.DECEMBER, 24)
        while (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) date = date.minusDays(1)
        return date
    }

    private fun goodFriday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day).minusDays(2)
    }
}

data class ContractHoliday(val date: LocalDate, val name: String)
