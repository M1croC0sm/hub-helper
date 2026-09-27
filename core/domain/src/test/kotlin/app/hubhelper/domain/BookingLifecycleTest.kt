package app.hubhelper.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BookingLifecycleTest {
    private val date = LocalDate.of(2026, 9, 1)
    private val booking = BookedPtoDay("1", date, durationMinutes = 240, stableId = "stable", legacyAssumption = false)
    private fun balance(days: List<BookedPtoDay>, adjustments: List<TimeBalanceAdjustment> = emptyList(), shift: Int = 600) =
        TimeOffCalculator.balanceHours(TimeBalanceKind.PTO, "40", date, null, date, adjustments, bookedPtoDays = days, regularBookedPtoMinutes = shift)
    @Test fun `partial booking retains duration after shift change`() {
        assertEquals("36", balance(listOf(booking), shift = 480))
        assertEquals("36", balance(listOf(booking), shift = 600))
    }
    @Test fun `cancelled and requested bookings do not deduct`() {
        assertEquals("40", balance(listOf(booking.copy(status = BookingStatus.CANCELLED))))
        assertEquals("40", balance(listOf(booking.copy(status = BookingStatus.REQUESTED))))
    }
    @Test fun `linked usage replaces booking deduction`() {
        assertEquals("37", balance(listOf(booking), listOf(TimeBalanceAdjustment("use", date, TimeBalanceKind.PTO, -180, null, "stable"))))
    }
    @Test fun `unrelated same day adjustment is separate`() {
        assertEquals("35", balance(listOf(booking), listOf(TimeBalanceAdjustment("use", date, TimeBalanceKind.PTO, -60, null))))
    }
    @Test fun `zero floating allowance prevents booking`() {
        assertFalse(floatingHolidayAvailable(BookedTimeType.ANYTIME_FLOATING, emptyList(), emptyList(), 2026, 0))
    }
    @Test fun `linked call in consumes PTO once`() {
        assertEquals("32", TimeOffCalculator.balanceHours(TimeBalanceKind.PTO, "40", date, null, date,
            emptyList(), callIns = listOf(CallInEvent("call", date, 480, "stable")), bookedPtoDays = listOf(booking)))
    }

}
