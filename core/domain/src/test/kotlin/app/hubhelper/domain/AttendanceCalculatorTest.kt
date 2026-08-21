package app.hubhelper.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceCalculatorTest {
    private val calculator = AttendanceCalculator()

    @Test
    fun `confirmed points remain active until anniversary date`() {
        val event = event("2025-11-15", 2, AttendanceEventStatus.CONFIRMED)

        assertEquals(2, calculator.summarize(listOf(event), date("2026-11-14")).confirmedPoints.value)
        assertEquals(0, calculator.summarize(listOf(event), date("2026-11-15")).confirmedPoints.value)
    }

    @Test
    fun `half points are summed without floating point arithmetic`() {
        val events = listOf(
            event("2026-01-01", 1, AttendanceEventStatus.CONFIRMED),
            event("2026-02-01", 1, AttendanceEventStatus.CONFIRMED),
        )

        assertEquals("1", calculator.summarize(events, date("2026-03-01")).confirmedPoints.asDisplayValue())
    }

    @Test
    fun `pending and non-counting statuses do not change confirmed total`() {
        val events = listOf(
            event("2026-01-01", 2, AttendanceEventStatus.PENDING),
            event("2026-01-02", 2, AttendanceEventStatus.EXCUSED),
            event("2026-01-03", 2, AttendanceEventStatus.DISPUTED),
            event("2026-01-04", 2, AttendanceEventStatus.RESCINDED),
        )

        val summary = calculator.summarize(events, date("2026-02-01"))
        assertEquals(0, summary.confirmedPoints.value)
        assertEquals(1, summary.pendingEventCount)
    }

    @Test
    fun `next expiration is earliest active confirmed event`() {
        val events = listOf(
            event("2026-05-02", 2, AttendanceEventStatus.CONFIRMED),
            event("2026-04-01", 1, AttendanceEventStatus.CONFIRMED),
        )

        assertEquals(date("2027-04-01"), calculator.summarize(events, date("2026-06-01")).nextExpirationDate)
    }

    @Test
    fun `attendance credits reduce balance and do not expire`() {
        val charged = event("2026-01-01", 4, AttendanceEventStatus.CONFIRMED)
        val credit = AttendanceEvent(
            id = "credit",
            occurredOn = date("2026-04-01"),
            type = AttendanceEventType.ATTENDANCE_CREDIT,
            points = HalfPoints(2),
            status = AttendanceEventStatus.CONFIRMED,
        )
        assertEquals("1", calculator.summarize(listOf(charged, credit), date("2026-05-01")).confirmedPoints.asDisplayValue())
        assertEquals("-1", calculator.summarize(listOf(charged, credit), date("2027-02-01")).confirmedPoints.asDisplayValue())
    }

    @Test
    fun `breakdown separates active charges credits and excluded entries`() {
        val charged = event("2026-01-01", 3, AttendanceEventStatus.CONFIRMED)
        val pending = event("2026-02-01", 2, AttendanceEventStatus.PENDING)
        val credit = AttendanceEvent(
            id = "credit",
            occurredOn = date("2026-03-01"),
            type = AttendanceEventType.ATTENDANCE_CREDIT,
            points = HalfPoints(2),
            status = AttendanceEventStatus.CONFIRMED,
        )

        val breakdown = calculator.breakdown(listOf(charged, pending, credit), date("2026-04-01"))

        assertEquals(3, breakdown.confirmedCharges.value)
        assertEquals(2, breakdown.confirmedCredits.value)
        assertEquals(listOf(charged, credit), breakdown.includedEvents)
        assertEquals(listOf(pending), breakdown.excludedEvents)
    }

    @Test
    fun `next attendance credit is 90 days after the latest confirmed point`() {
        val older = event("2026-01-01", 2, AttendanceEventStatus.CONFIRMED)
        val latest = event("2026-02-15", 1, AttendanceEventStatus.CONFIRMED)

        assertEquals(
            date("2026-05-16"),
            calculator.nextAttendanceCreditDate(listOf(older, latest), date("2026-03-01")),
        )
    }

    @Test
    fun `recorded credit advances the next 90 day period`() {
        val charged = event("2026-01-01", 2, AttendanceEventStatus.CONFIRMED)
        val credit = AttendanceEvent(
            id = "credit",
            occurredOn = date("2026-04-01"),
            type = AttendanceEventType.ATTENDANCE_CREDIT,
            points = HalfPoints(2),
            status = AttendanceEventStatus.CONFIRMED,
        )

        assertEquals(
            date("2026-06-30"),
            calculator.nextAttendanceCreditDate(listOf(charged, credit), date("2026-04-02")),
        )
    }

    private fun event(date: String, halfPoints: Int, status: AttendanceEventStatus) = AttendanceEvent(
        id = date,
        occurredOn = date(date),
        type = AttendanceEventType.UNEXCUSED_ABSENCE,
        points = HalfPoints(halfPoints),
        status = status,
    )

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
