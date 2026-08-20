package app.hubhelper.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceColorBandTest {
    @Test fun `three or below is green`() {
        assertEquals(AttendanceColorBand.GREEN, attendanceColorBand(BigDecimal("3")))
        assertEquals(AttendanceColorBand.GREEN, attendanceColorBand(BigDecimal("-1")))
    }

    @Test fun `above three through five is orange`() {
        assertEquals(AttendanceColorBand.ORANGE, attendanceColorBand(BigDecimal("3.5")))
        assertEquals(AttendanceColorBand.ORANGE, attendanceColorBand(BigDecimal("5")))
    }

    @Test fun `above five is red`() {
        assertEquals(AttendanceColorBand.RED, attendanceColorBand(BigDecimal("5.5")))
    }
}
