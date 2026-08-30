package app.hubhelper.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object PaydayCalculator {
    fun followingFridays(from: LocalDate = LocalDate.now()): List<LocalDate> {
        val first = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        return listOf(first, first.plusWeeks(1))
    }

    fun everyOtherFriday(anchor: LocalDate, through: LocalDate): List<LocalDate> {
        return buildList {
            var next = anchor
            while (!next.isAfter(through)) { if (next.dayOfWeek == DayOfWeek.FRIDAY) add(next); next = next.plusWeeks(2) }
        }
    }
}
