package app.hubhelper.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HolidayCalendarParserTest {
    @Test fun `parses numeric and named holiday rows`() {
        val result = HolidayCalendarParser().parse(
            """
            New Year's Day 1/1/2027
            Memorial Day May 31, 2027
            07-04 Independence Day
            """.trimIndent(),
            2027,
        )
        assertEquals(
            listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 5, 31), LocalDate.of(2027, 7, 4)),
            result.holidays.map { it.date },
        )
        assertEquals("New Year's Day", result.holidays.first().name)
    }

    @Test fun `uses adjacent OCR line as holiday name`() {
        val result = HolidayCalendarParser().parse("9/7/2026\nLabor Day", 2026)
        assertEquals("Labor Day", result.holidays.single().name)
    }

    @Test fun `parses holiday name weekday and date on the same line`() {
        val result = HolidayCalendarParser().parse("Labor Day, Monday September 7", 2026)
        assertEquals(LocalDate.of(2026, 9, 7), result.holidays.single().date)
        assertEquals("Labor Day", result.holidays.single().name)
    }

    @Test fun `parses weekday date followed by holiday name`() {
        val result = HolidayCalendarParser().parse("Monday, September 7 - Labor Day", 2026)
        assertEquals(LocalDate.of(2026, 9, 7), result.holidays.single().date)
        assertEquals("Labor Day", result.holidays.single().name)
    }

    @Test fun `replaces generic plant holiday with Good Friday`() {
        val result = HolidayCalendarParser().parse("4/3/2026 Plant Holiday", 2026)
        assertEquals("Good Friday", result.holidays.single().name)
    }

    @Test fun `replaces generic plant holiday with Christmas Day`() {
        val result = HolidayCalendarParser().parse("12/25/2026 Plant Holiday", 2026)
        assertEquals("Christmas Day", result.holidays.single().name)
    }

    @Test fun `fixed holiday dates override incorrect floater labels`() {
        val result = HolidayCalendarParser().parse(
            "1/1/2026 Floater\n7/4/2026\n12/25/2026 Floating Holiday",
            2026,
        )
        assertEquals(listOf("New Year's Day", "Independence Day", "Christmas Day"), result.holidays.map { it.name })
    }

    @Test fun `undated floating holidays are not added to the plant calendar`() {
        val result = HolidayCalendarParser().parse("Floating Holiday\nPersonal Floater", 2026)
        assertEquals(emptyList<ParsedHoliday>(), result.holidays)
    }

    @Test fun `renames last working day before Christmas`() {
        val result = HolidayCalendarParser().parse("12/24/2026 Last working day before Christmas.", 2026)
        assertEquals("Christmas", result.holidays.single().name)
    }

    @Test fun `labels observed Independence Day when OCR only finds the date`() {
        val result = HolidayCalendarParser().parse("7/3/2026", 2026)
        assertEquals("Independence Day", result.holidays.single().name)
    }
}
