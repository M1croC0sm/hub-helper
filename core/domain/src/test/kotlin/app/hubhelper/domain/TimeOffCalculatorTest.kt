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

    @Test fun `call in color bands show increasing urgency`() {
        assertEquals(CallInColorBand.GREEN, callInColorBand(5))
        assertEquals(CallInColorBand.GREEN, callInColorBand(4))
        assertEquals(CallInColorBand.ORANGE, callInColorBand(3))
        assertEquals(CallInColorBand.ORANGE, callInColorBand(2))
        assertEquals(CallInColorBand.RED, callInColorBand(1))
        assertEquals(CallInColorBand.RED, callInColorBand(0))
    }

    @Test fun `upcoming PTO summary includes only future approved regular bookings`() {
        val today = LocalDate.of(2026, 9, 27)
        val days = listOf(
            BookedPtoDay("past", today.minusDays(1), durationMinutes = 600),
            BookedPtoDay("today", today, durationMinutes = 600),
            BookedPtoDay("approved", today.plusDays(2), durationMinutes = 300),
            BookedPtoDay("legacy-duration", today.plusDays(4)),
            BookedPtoDay("requested", today.plusDays(5), durationMinutes = 600, status = BookingStatus.REQUESTED),
            BookedPtoDay("cancelled", today.plusDays(6), durationMinutes = 600, status = BookingStatus.CANCELLED),
            BookedPtoDay("floating", today.plusDays(7), type = BookedTimeType.ANYTIME_FLOATING, durationMinutes = 480),
        )

        val summary = upcomingApprovedPto(days, today, defaultDurationMinutes = 600)

        assertEquals(2, summary.bookingCount)
        assertEquals(Minutes(900), summary.minutes)
        assertEquals(today.plusDays(2), summary.nextDate)
        assertEquals(today.plusDays(4), summary.throughDate)
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

    @Test fun `removing a floating holiday record restores its availability`() {
        val asOf = LocalDate.of(2026, 8, 20)
        val used = TimeBalanceAdjustment("used", asOf, TimeBalanceKind.FLOATING_ANYTIME, -480, null)
        val booked = BookedPtoDay("booked", asOf, type = BookedTimeType.BIRTHDAY_FLOATING)

        assertEquals(false, floatingHolidayAvailable(BookedTimeType.ANYTIME_FLOATING, listOf(used), listOf(booked), 2026))
        assertEquals(false, floatingHolidayAvailable(BookedTimeType.BIRTHDAY_FLOATING, listOf(used), listOf(booked), 2026))
        assertEquals(true, floatingHolidayAvailable(BookedTimeType.ANYTIME_FLOATING, emptyList(), listOf(booked), 2026))
        assertEquals(true, floatingHolidayAvailable(BookedTimeType.BIRTHDAY_FLOATING, listOf(used), emptyList(), 2026))
    }
}
