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

    @Test fun `second shift keeps thanksgiving holidays on consecutive days`() {
        val first = ContractHolidayCalculator.forYear(2026, secondShift = false)
        val second = ContractHolidayCalculator.forYear(2026, secondShift = true)
        assertEquals(LocalDate.of(2026, 11, 26), first.single { it.name == "Thanksgiving Day" }.date)
        assertEquals(LocalDate.of(2026, 11, 27), first.single { it.name == "Friday after Thanksgiving" }.date)
        assertEquals(LocalDate.of(2026, 11, 25), second.single { it.name == "Thanksgiving Day" }.date)
        assertEquals(LocalDate.of(2026, 11, 26), second.single { it.name == "Friday after Thanksgiving" }.date)
        assertEquals(second.size, second.map { it.date }.distinct().size)
    }

    @Test fun `christmas holidays have distinct dates and clear names`() {
        val first2026 = ContractHolidayCalculator.forYear(2026, secondShift = false)
        assertEquals(LocalDate.of(2026, 12, 24), first2026.single { it.name == "Christmas Eve" }.date)
        assertEquals(LocalDate.of(2026, 12, 25), first2026.single { it.name == "Christmas Day" }.date)

        val second2026 = ContractHolidayCalculator.forYear(2026, secondShift = true)
        assertEquals(LocalDate.of(2026, 12, 23), second2026.single { it.name == "Christmas Holiday" }.date)
        assertEquals(LocalDate.of(2026, 12, 24), second2026.single { it.name == "Christmas Day" }.date)

        val first2027 = ContractHolidayCalculator.forYear(2027, secondShift = false)
        assertEquals(LocalDate.of(2027, 12, 23), first2027.single { it.name == "Christmas Holiday" }.date)
        assertEquals(LocalDate.of(2027, 12, 24), first2027.single { it.name == "Christmas Day" }.date)

        val second2027 = ContractHolidayCalculator.forYear(2027, secondShift = true)
        assertEquals(LocalDate.of(2027, 12, 22), second2027.single { it.name == "Christmas Holiday" }.date)
        assertEquals(LocalDate.of(2027, 12, 23), second2027.single { it.name == "Christmas Day" }.date)
    }

    @Test fun `new years follows first and second shift across year boundary`() {
        assertEquals(
            LocalDate.of(2027, 1, 1),
            ContractHolidayCalculator.forYear(2027, false).single { it.name == "New Year's Day" }.date,
        )
        assertEquals(
            LocalDate.of(2026, 12, 31),
            ContractHolidayCalculator.forYear(2027, true).single { it.name == "New Year's Day" }.date,
        )
        assertEquals(
            LocalDate.of(2027, 12, 31),
            ContractHolidayCalculator.forYear(2028, false).single { it.name == "New Year's Day" }.date,
        )
        assertEquals(
            LocalDate.of(2027, 12, 30),
            ContractHolidayCalculator.forYear(2028, true).single { it.name == "New Year's Day" }.date,
        )
    }
}
