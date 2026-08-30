package app.hubhelper.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PaydayCalculatorTest {
    @Test fun `generates every other friday through year end`() {
        val dates = PaydayCalculator.everyOtherFriday(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 12, 31))
        assertEquals(listOf("2026-08-28", "2026-09-11", "2026-09-25", "2026-10-09", "2026-10-23", "2026-11-06", "2026-11-20", "2026-12-04", "2026-12-18"), dates.map(LocalDate::toString))
    }
}
