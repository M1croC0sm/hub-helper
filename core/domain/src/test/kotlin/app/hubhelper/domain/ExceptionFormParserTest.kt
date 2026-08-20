package app.hubhelper.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExceptionFormParserTest {
    @Test fun `finds distinct booked vacation dates`() {
        val result = ExceptionFormParser().parse(
            "Vacation exception approved for 09/12/2026 and PTO 10-03-26. Duplicate 09/12/2026",
            LocalDate.of(2026, 8, 18),
        )
        assertEquals(listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 10, 3)), result.bookedDates)
    }

    @Test fun `warns when no date is readable`() {
        assertTrue(ExceptionFormParser().parse("Vacation approved", LocalDate.of(2026, 8, 18)).warnings.isNotEmpty())
    }
}
