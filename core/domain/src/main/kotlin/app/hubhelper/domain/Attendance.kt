package app.hubhelper.domain

import java.time.LocalDate

/** Half-points avoid floating-point errors while still representing the policy exactly. */
@JvmInline
value class HalfPoints(val value: Int) {
    fun asDisplayValue(): String = when {
        value % 2 == 0 -> (value / 2).toString()
        value < 0 -> "-${kotlin.math.abs(value) / 2}.5"
        else -> "${value / 2}.5"
    }
}

enum class AttendanceEventType {
    UNEXCUSED_ABSENCE,
    TARDY,
    LEFT_EARLY,
    CALL_IN_VIOLATION,
    ATTENDANCE_CREDIT,
}

enum class AttendanceEventStatus {
    PENDING,
    CONFIRMED,
    EXCUSED,
    DISPUTED,
    RESCINDED,
}

data class SourceReference(
    val documentId: String,
    val pageNumber: Int?,
    val policyVersion: String?,
)

data class AttendanceEvent(
    val id: String,
    val occurredOn: LocalDate,
    val type: AttendanceEventType,
    val points: HalfPoints,
    val status: AttendanceEventStatus,
    val source: SourceReference? = null,
    val note: String? = null,
)

data class AttendanceSummary(
    val confirmedPoints: HalfPoints,
    val pendingEventCount: Int,
    val nextExpirationDate: LocalDate?,
)

data class AttendanceBreakdown(
    val confirmedCharges: HalfPoints,
    val confirmedCredits: HalfPoints,
    val includedEvents: List<AttendanceEvent>,
    val excludedEvents: List<AttendanceEvent>,
)

/** Implements only reviewed, unambiguous policy rules. */
class AttendanceCalculator(
    private val expirationMonths: Long = 12,
) {
    fun expiresOn(event: AttendanceEvent): LocalDate =
        event.occurredOn.plusMonths(expirationMonths)

    fun nextAttendanceCreditDate(events: List<AttendanceEvent>, asOf: LocalDate): LocalDate? {
        val lastConfirmedPoint = events
            .filter {
                it.type != AttendanceEventType.ATTENDANCE_CREDIT &&
                    it.status == AttendanceEventStatus.CONFIRMED &&
                    !it.occurredOn.isAfter(asOf)
            }
            .maxByOrNull { it.occurredOn }
            ?: return null
        val creditsSinceLastPoint = events.count {
            it.type == AttendanceEventType.ATTENDANCE_CREDIT &&
                it.status == AttendanceEventStatus.CONFIRMED &&
                !it.occurredOn.isBefore(lastConfirmedPoint.occurredOn) &&
                !it.occurredOn.isAfter(asOf)
        }
        return lastConfirmedPoint.occurredOn.plusDays(90L * (creditsSinceLastPoint + 1))
    }

    fun breakdown(events: List<AttendanceEvent>, asOf: LocalDate): AttendanceBreakdown {
        val included = events.filter { event ->
            event.status == AttendanceEventStatus.CONFIRMED &&
                !event.occurredOn.isAfter(asOf) &&
                (event.type == AttendanceEventType.ATTENDANCE_CREDIT || asOf.isBefore(expiresOn(event)))
        }
        return AttendanceBreakdown(
            confirmedCharges = HalfPoints(included.filterNot { it.type == AttendanceEventType.ATTENDANCE_CREDIT }.sumOf { it.points.value }),
            confirmedCredits = HalfPoints(included.filter { it.type == AttendanceEventType.ATTENDANCE_CREDIT }.sumOf { it.points.value }),
            includedEvents = included,
            excludedEvents = events.filterNot(included::contains),
        )
    }

    fun summarize(events: List<AttendanceEvent>, asOf: LocalDate): AttendanceSummary {
        val activeConfirmed = breakdown(events, asOf).includedEvents

        val pendingCount = events.count {
            it.status == AttendanceEventStatus.PENDING && !it.occurredOn.isAfter(asOf)
        }

        return AttendanceSummary(
            confirmedPoints = HalfPoints(
                activeConfirmed.sumOf { event ->
                    if (event.type == AttendanceEventType.ATTENDANCE_CREDIT) -event.points.value else event.points.value
                }.coerceAtLeast(-2),
            ),
            pendingEventCount = pendingCount,
            nextExpirationDate = activeConfirmed
                .filterNot { it.type == AttendanceEventType.ATTENDANCE_CREDIT }
                .minOfOrNull(::expiresOn),
        )
    }
}
