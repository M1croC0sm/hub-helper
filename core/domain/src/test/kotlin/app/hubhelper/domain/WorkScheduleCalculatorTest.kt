package app.hubhelper.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkScheduleCalculatorTest {
    private val calculator = WorkScheduleCalculator()

    @Test
    fun `first shift weekday overtime starts one hour earlier`() {
        val shift = calculator.shift(LocalDate.parse("2026-08-17"), ShiftPreset.FIRST, overtimeActive = true)!!
        assertEquals(LocalTime.of(5, 0), shift.startsAt.toLocalTime())
        assertEquals(LocalTime.of(14, 30), shift.endsAt.toLocalTime())
        assertTrue(shift.overtime)
    }

    @Test
    fun `first shift Saturday exists only when overtime is active`() {
        val saturday = LocalDate.parse("2026-08-22")
        assertNull(calculator.shift(saturday, ShiftPreset.FIRST))
        assertEquals(LocalTime.of(11, 0), calculator.shift(saturday, ShiftPreset.FIRST, true)!!.endsAt.toLocalTime())
    }

    @Test
    fun `second shift crosses midnight`() {
        val date = LocalDate.parse("2026-08-17")
        val shift = calculator.shift(date, ShiftPreset.SECOND)!!
        assertEquals(date.plusDays(1), shift.endsAt.toLocalDate())
        assertEquals(LocalTime.of(0, 45), shift.endsAt.toLocalTime())
        assertFalse(shift.overtime)
    }

    @Test
    fun `second shift Friday overtime uses reported hours`() {
        val shift = calculator.shift(LocalDate.parse("2026-08-21"), ShiftPreset.SECOND, true)!!
        assertEquals(LocalTime.of(14, 15), shift.startsAt.toLocalTime())
        assertEquals(LocalTime.of(20, 15), shift.endsAt.toLocalTime())
        assertTrue(shift.overtime)
    }
}

