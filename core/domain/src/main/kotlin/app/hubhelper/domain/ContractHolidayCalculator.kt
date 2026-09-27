package app.hubhelper.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** Holidays named in CBA Article XIII, excluding the roving and birthday holidays. */
object ContractHolidayCalculator {
    fun forYear(year: Int, secondShift: Boolean): List<ContractHoliday> {
        val christmasDay = observedDate(LocalDate.of(year, Month.DECEMBER, 25))
        val christmasHoliday = previousWeekday(christmasDay)
        val fixed = listOf(
            LocalDate.of(year, Month.JANUARY, 1) to "New Year's Day",
            goodFriday(year) to "Good Friday",
            LocalDate.of(year, Month.MAY, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)) to "Memorial Day",
            LocalDate.of(year, Month.JULY, 4) to "Independence Day",
            LocalDate.of(year, Month.SEPTEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)) to "Labor Day",
            LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)) to "Thanksgiving Day",
            LocalDate.of(year, Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)).plusDays(1) to "Friday after Thanksgiving",
            christmasHoliday to if (christmasHoliday.dayOfMonth == 24) "Christmas Eve" else "Christmas Holiday",
            LocalDate.of(year, Month.DECEMBER, 25) to "Christmas Day",
        )
        val firstShift = fixed.map { (date, name) -> ContractHoliday(observedDate(date), name) }
        if (!secondShift) return firstShift.distinctBy { it.date to it.name }.sortedBy { it.date }

        // A first-shift Friday closure is observed Thursday by second shift. If that
        // collides with the preceding holiday in a consecutive block, keep both days
        // by moving the earlier holiday to the preceding scheduled weekday.
        val occupied = mutableSetOf<LocalDate>()
        return firstShift.asReversed().map { holiday ->
            var date = if (holiday.date.dayOfWeek == DayOfWeek.FRIDAY) holiday.date.minusDays(1) else holiday.date
            while (date in occupied || date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                date = previousWeekday(date)
            }
            occupied += date
            holiday.copy(
                date = date,
                name = if (holiday.name == "Christmas Eve" && date.dayOfMonth != 24) "Christmas Holiday" else holiday.name,
            )
        }.asReversed()
            .distinctBy { it.date to it.name }
            .sortedBy { it.date }
    }

    private fun observedDate(date: LocalDate): LocalDate = when {
        date.dayOfWeek == DayOfWeek.SATURDAY -> date.minusDays(1)
        date.dayOfWeek == DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun previousWeekday(date: LocalDate): LocalDate {
        var previous = date.minusDays(1)
        while (previous.dayOfWeek == DayOfWeek.SATURDAY || previous.dayOfWeek == DayOfWeek.SUNDAY) {
            previous = previous.minusDays(1)
        }
        return previous
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
