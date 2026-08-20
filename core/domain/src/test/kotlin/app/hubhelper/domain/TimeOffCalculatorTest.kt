package app.hubhelper.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeOffCalculatorTest {
    @Test fun `low PTO warning uses the selected shift day`() {
        assertEquals(true, TimeOffCalculator.isAtOrBelowOnePtoDay("8", 8))
        assertEquals(false, TimeOffCalculator.isAtOrBelowOnePtoDay("9", 8))
        assertEquals(true, TimeOffCalculator.isAtOrBelowOnePtoDay("10", 10))
        assertEquals(false, TimeOffCalculator.isAtOrBelowOnePtoDay("11", 10))
    }

    @Test fun `new hire allocation follows contract month table`() {
        assertEquals(80, TimeOffCalculator.vacationHoursForYear(LocalDate.of(2026, 1, 20), 2026))
        assertEquals(72, TimeOffCalculator.vacationHoursForYear(LocalDate.of(2026, 3, 20), 2026))
        assertEquals(32, TimeOffCalculator.vacationHoursForYear(LocalDate.of(2026, 9, 20), 2026))
        assertEquals(8, TimeOffCalculator.vacationHoursForYear(LocalDate.of(2026, 12, 20), 2026))
    }

    @Test fun `january allocation follows service tiers`() {
        val hireDate = LocalDate.of(2005, 8, 16)
        assertEquals(80, TimeOffCalculator.vacationHoursForYear(hireDate, 2010))
        assertEquals(120, TimeOffCalculator.vacationHoursForYear(hireDate, 2011))
        assertEquals(160, TimeOffCalculator.vacationHoursForYear(hireDate, 2016))
        assertEquals(200, TimeOffCalculator.vacationHoursForYear(hireDate, 2026))
    }

    @Test fun `sick balance resets to eight hours for a new calendar year`() {
        assertEquals(
            "8",
            TimeOffCalculator.balanceHours(
                kind = TimeBalanceKind.SICK,
                enteredBalanceHours = "2",
                enteredBalanceDate = LocalDate.of(2026, 8, 16),
                hireDate = null,
                asOf = LocalDate.of(2027, 1, 1),
                adjustments = emptyList(),
            ),
        )
    }

    @Test fun `call ins consume the recorded shift day from PTO`() {
        assertEquals(
            "62",
            TimeOffCalculator.balanceHours(
                kind = TimeBalanceKind.PTO,
                enteredBalanceHours = "80",
                enteredBalanceDate = LocalDate.of(2026, 1, 1),
                hireDate = null,
                asOf = LocalDate.of(2026, 8, 20),
                adjustments = emptyList(),
                callIns = listOf(
                    CallInEvent("1", LocalDate.of(2026, 2, 1), 8 * 60),
                    CallInEvent("2", LocalDate.of(2026, 3, 1), 10 * 60),
                ),
            ),
        )
    }

    @Test fun `call in allowance resets by calendar year`() {
        val events = (1..5).map { CallInEvent(it.toString(), LocalDate.of(2026, it, 1), 480) }
        assertEquals(0, remainingCallIns(events, 2026))
        assertEquals(5, remainingCallIns(events, 2027))
    }

    @Test fun `booked PTO deducts the shift day when its date arrives`() {
        val booking = BookedPtoDay("1", LocalDate.of(2026, 8, 20))
        val before = TimeOffCalculator.balanceHours(
            TimeBalanceKind.PTO, "40", LocalDate.of(2026, 8, 1), null,
            LocalDate.of(2026, 8, 19), emptyList(), bookedPtoDays = listOf(booking), regularBookedPtoMinutes = 10 * 60,
        )
        val onDate = TimeOffCalculator.balanceHours(
            TimeBalanceKind.PTO, "40", LocalDate.of(2026, 8, 1), null,
            LocalDate.of(2026, 8, 20), emptyList(), bookedPtoDays = listOf(booking), regularBookedPtoMinutes = 10 * 60,
        )
        assertEquals("40", before)
        assertEquals("30", onDate)
    }

    @Test fun `booked PTO is not deducted twice when time was also recorded`() {
        val date = LocalDate.of(2026, 8, 20)
        assertEquals(
            "32",
            TimeOffCalculator.balanceHours(
                TimeBalanceKind.PTO, "40", LocalDate.of(2026, 8, 1), null, date,
                listOf(TimeBalanceAdjustment("1", date, TimeBalanceKind.PTO, -480, null)),
                bookedPtoDays = listOf(BookedPtoDay("1", date)),
            ),
        )
    }

    @Test fun `floating holidays use their own two day allowance`() {
        val asOf = LocalDate.of(2026, 8, 20)
        assertEquals(2, remainingFloatingHolidays(emptyList(), emptyList(), asOf))
        assertEquals(
            1,
            remainingFloatingHolidays(
                emptyList(),
                listOf(BookedPtoDay("1", asOf, type = BookedTimeType.BIRTHDAY_FLOATING)),
                asOf,
            ),
        )
    }
}
