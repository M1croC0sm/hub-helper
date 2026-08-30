package app.hubhelper.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractHolidayCalculatorTest {
    @Test fun `creates contract holidays and excludes personal floaters`() {
        val holidays = ContractHolidayCalculator.forYear(2026, secondShift = false)
        assertEquals(9, holidays.size)
        assertTrue(holidays.none { it.name.contains("birthday", ignoreCase = true) || it.name.contains("roving", ignoreCase = true) })
        assertEquals("Independence Day", holidays.first { it.name == "Independence Day" }.name)
    }

    @Test fun `second shift observes friday holiday thursday`() {
        val first = ContractHolidayCalculator.forYear(2026, secondShift = false)
        val second = ContractHolidayCalculator.forYear(2026, secondShift = true)
        assertTrue(first.any { it.date.dayOfWeek == DayOfWeek.FRIDAY && it.name == "Friday after Thanksgiving" })
        assertTrue(second.any { it.date == LocalDate.of(2026, 11, 26) && it.name == "Friday after Thanksgiving" })
    }
}
