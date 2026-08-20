package app.hubhelper.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period

object TimeOffCalculator {
    const val ANNUAL_SICK_HOURS = 8

    fun isAtOrBelowOnePtoDay(balanceHours: String, shiftDayHours: Int): Boolean =
        balanceHours.toBigDecimalOrNull()?.let { it <= shiftDayHours.toBigDecimal() } == true

    fun vacationHoursForYear(hireDate: LocalDate, year: Int): Int {
        require(year >= hireDate.year)
        if (year == hireDate.year) {
            return when (hireDate.monthValue) {
                1 -> 80
                2, 3 -> 72
                4 -> 64
                5 -> 56
                6 -> 48
                7 -> 40
                8, 9 -> 32
                10 -> 24
                11 -> 16
                else -> 8
            }
        }
        val yearsOnJanuaryFirst = Period.between(hireDate, LocalDate.of(year, 1, 1)).years
        return when {
            yearsOnJanuaryFirst >= 20 -> 200
            yearsOnJanuaryFirst >= 10 -> 160
            yearsOnJanuaryFirst >= 5 -> 120
            else -> 80
        }
    }

    fun serviceDescription(hireDate: LocalDate, asOf: LocalDate): String {
        if (asOf.isBefore(hireDate)) return "Employment has not started"
        val period = Period.between(hireDate, asOf)
        return "${period.years} years, ${period.months} months"
    }

    fun balanceHours(
        kind: TimeBalanceKind,
        enteredBalanceHours: String,
        enteredBalanceDate: LocalDate,
        hireDate: LocalDate?,
        asOf: LocalDate,
        adjustments: List<TimeBalanceAdjustment>,
        callIns: List<CallInEvent> = emptyList(),
        bookedPtoDays: List<BookedPtoDay> = emptyList(),
        regularBookedPtoMinutes: Int = 8 * 60,
    ): String {
        val hasAnnualReset = asOf.year > enteredBalanceDate.year &&
            (kind != TimeBalanceKind.PTO || hireDate != null)
        val baseHours: BigDecimal = if (hasAnnualReset) {
            when (kind) {
                TimeBalanceKind.PTO -> vacationHoursForYear(requireNotNull(hireDate), asOf.year)
                TimeBalanceKind.SICK -> ANNUAL_SICK_HOURS
                TimeBalanceKind.FLOATING_BIRTHDAY, TimeBalanceKind.FLOATING_ANYTIME -> 0
            }.toBigDecimal()
        } else {
            enteredBalanceHours.toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
        val firstIncludedDate = if (hasAnnualReset) LocalDate.of(asOf.year, 1, 1) else enteredBalanceDate
        val changedMinutes = adjustments
            .filter { it.kind == kind && !it.occurredOn.isBefore(firstIncludedDate) && !it.occurredOn.isAfter(asOf) }
            .sumOf { it.minutes }
        val callInMinutes = if (kind == TimeBalanceKind.PTO) {
            callIns.filter { !it.occurredOn.isBefore(firstIncludedDate) && !it.occurredOn.isAfter(asOf) }.sumOf { it.ptoMinutes }
        } else 0
        val separatelyRecordedPtoDates = adjustments.filter {
            it.kind == TimeBalanceKind.PTO && it.minutes < 0 && !it.occurredOn.isBefore(firstIncludedDate) && !it.occurredOn.isAfter(asOf)
        }.mapTo(mutableSetOf()) { it.occurredOn }
        val bookedMinutes = if (kind == TimeBalanceKind.PTO) {
            bookedPtoDays.filter {
                it.type == BookedTimeType.REGULAR_PTO &&
                    !it.date.isBefore(firstIncludedDate) && !it.date.isAfter(asOf) &&
                    it.date !in separatelyRecordedPtoDates
            }.sumOf { regularBookedPtoMinutes }
        } else 0
        return baseHours
            .add(BigDecimal(changedMinutes - callInMinutes - bookedMinutes).divide(BigDecimal(60)))
            .stripTrailingZeros()
            .toPlainString()
    }
}
