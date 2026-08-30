package app.hubhelper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendancePrintoutParserTest {
    @Test
    fun `extracts final running total and signed adjustments`() {
        val text = """
            Point History
            3-14-24 .5pt Left 4hrs early .5
            6-13-24 -1pt 90 day roll off -.5
            8-19-24 1pt Absent Wife Sick .5
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(3, result.rows.size)
        assertEquals(1, result.currentTotalHalfPoints)
        assertEquals(-2, result.rows[1].adjustmentHalfPoints)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `refuses to invent a total without complete dated rows`() {
        val result = AttendancePrintoutParser().parse("Employee Attendance Notice\nPoints to Date")
        assertEquals(null, result.currentTotalHalfPoints)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `reads final standalone total and infers a missing row adjustment from uploaded sheet`() {
        val text = """
            Point History
            Date Comment Points to Date
            3-14-24 .5pt Left 4hrs early .5
            6-13-24 -1pt 90 day roll off -.5
            8-19-24 1pt Absent Wife Sick .5
            8-20-24 1pt Absent Wife Sick 1.5
            8-21-24 1.5pts Call in late 6:42AM Wife sick 3.0
            9-18-24 .5pt Tardy 3.5
            9-30-24 Absent Call in sick 4.5
            12-30-24 -1pt 90 day roll off 3.5
            12-31-24 1pt Absent Call in sick 4.5
            3-14-25 -.5pt 1 year roll off 4.0
            4-1-25 -1pt 90 day roll off 3.0
            6-3-25 1pt Absent 4.0
            8-19-25 -1pt 90 day roll off 3.0
            8-20-25 -1pt 90 day roll off 2.0
            8-21-25 -1.5pt 90day roll off 0.5
            9-02-25 -.5pt 90day roll off -0.5
            9-18-25 -.5pt One year roll off -1
            -1
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(17, result.rows.size)
        assertEquals(-2, result.currentTotalHalfPoints)
        assertEquals(2, result.rows.first { it.date.toString() == "2024-09-30" }.adjustmentHalfPoints)
    }

    @Test
    fun `reads OCR that separates dates comments and totals into columns`() {
        val text = """
            Point History
            Date
            3-14-24
            6-13-24
            Comment
            .5pt Left early
            -1pt 90 day roll off
            Points to Date
            .5
            -.5
            -.5
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(2, result.rows.size)
        assertEquals(-1, result.currentTotalHalfPoints)
    }

    @Test
    fun `ignores dates outside the attendance table`() {
        val text = """
            Employee hired 1-2-20
            Printed 8-29-26
            Point History
            Date Comment Points to Date
            8-03-26 1pt Absent 1.0
            Supervisor signature 8-04-26
            8-04-26 1pt should not be read
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(1, result.rows.size)
        assertEquals("2026-08-03", result.rows.single().date.toString())
    }

    @Test
    fun `continues after signatures at the bottom of the first page`() {
        val text = """
            --- Page 1 ---
            Point History
            Date Comment Points to Date
            8-01-26 1pt Absent 1.0
            1.0
            Employee Signature
            --- Page 2 ---
            8-02-26 Absent 2.0
            2.0
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(2, result.rows.size)
        assertEquals("2026-08-02", result.rows.last().date.toString())
        assertEquals(4, result.currentTotalHalfPoints)
    }

    @Test
    fun `orders rows by date when pages arrive reversed`() {
        val text = """
            --- Page 2 ---
            8-02-26 Absent 2.0
            2.0
            --- Page 1 ---
            Point History
            Date Comment Points to Date
            8-01-26 1pt Absent 1.0
            Employee Signature
        """.trimIndent()

        val result = AttendancePrintoutParser().parse(text)

        assertEquals(listOf("2026-08-01", "2026-08-02"), result.rows.map { it.date.toString() })
        assertEquals(2, result.rows.last().adjustmentHalfPoints)
        assertEquals(4, result.currentTotalHalfPoints)
    }
}
