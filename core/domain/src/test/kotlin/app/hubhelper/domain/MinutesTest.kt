package app.hubhelper.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MinutesTest {
    @Test fun `minute amounts display without requiring a terminating decimal`() {
        listOf(0, 1, 2, 6, 15, 59, 60, -1, -59).forEach { minutes ->
            val today = LocalDate.of(2026, 9, 1)
            val result = TimeOffCalculator.balanceHours(TimeBalanceKind.PTO, "0", today, null, today,
                listOf(TimeBalanceAdjustment("test", today, TimeBalanceKind.PTO, minutes, null)))
            assertEquals(displayMinutesAsHours(minutes), result)
        }
    }
    @Test fun `input preserves exact minutes`() {
        assertEquals(15L, Minutes.fromHours("0.25").value)
        assertEquals(-60L, Minutes.fromHours("-1").value)
    }
    @Test(expected = ArithmeticException::class)
    fun `fractional minutes are not silently rounded`() { Minutes.fromHours("0.001") }
}
