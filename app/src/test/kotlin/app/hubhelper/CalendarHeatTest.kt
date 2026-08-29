package app.hubhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarHeatTest {
    @Test
    fun `month heat uses stable confirmed point bands`() {
        assertEquals(0, calendarMonthHeatLevel(-1))
        assertEquals(0, calendarMonthHeatLevel(0))
        assertEquals(1, calendarMonthHeatLevel(1)) // 0.5 points
        assertEquals(2, calendarMonthHeatLevel(2)) // 1.0 point
        assertEquals(3, calendarMonthHeatLevel(3)) // 1.5 points
        assertEquals(4, calendarMonthHeatLevel(4)) // 2.0 points
        assertEquals(4, calendarMonthHeatLevel(9))
    }
}
