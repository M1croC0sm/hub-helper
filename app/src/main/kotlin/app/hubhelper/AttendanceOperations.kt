package app.hubhelper

import app.hubhelper.domain.*
import app.hubhelper.data.AttendanceRepository
import java.time.LocalDate

private fun importedAttendanceType(comment: String, adjustmentHalfPoints: Int): AttendanceEventType {
    if (adjustmentHalfPoints < 0) return AttendanceEventType.ATTENDANCE_CREDIT
    val normalized = comment.lowercase()
    return when {
        "tardy" in normalized || "late" in normalized -> AttendanceEventType.TARDY
        "left" in normalized || "early" in normalized -> AttendanceEventType.LEFT_EARLY
        "call" in normalized -> AttendanceEventType.CALL_IN_VIOLATION
        else -> AttendanceEventType.UNEXCUSED_ABSENCE
    }
}

private fun displayHalfPoints(halfPoints: Int): String = HalfPoints(halfPoints).asDisplayValue()

internal data class AttendanceImportResult(
    val setup: SetupData,
    val addedCount: Int,
    val skippedCount: Int,
)

internal suspend fun applyAttendanceStatement(
    setup: SetupData,
    parsed: ParsedAttendanceStatement,
    documentId: String,
    calculationDate: LocalDate,
    repository: AttendanceRepository,
): AttendanceImportResult {
    val effectiveDate = parsed.statementDate ?: calculationDate
    require(!effectiveDate.isAfter(calculationDate)) { "Statement date cannot be in the future" }
    val usableRows = parsed.rows.filter {
        it.adjustmentHalfPoints != null && it.adjustmentHalfPoints != 0 && !isAnnualFalloff(it.comment)
    }
    var importedActiveTotal = 0
    var addedCount = 0
    usableRows.forEach { row ->
        val adjustment = row.adjustmentHalfPoints!!
        require(adjustment.toLong() in -10000L..10000L && !row.date.isAfter(effectiveDate)) { "Invalid statement row amount or date" }
        require(row.reviewedType == null || (row.reviewedType == AttendanceEventType.ATTENDANCE_CREDIT) == (adjustment < 0)) { "Point sign does not match the reviewed event type" }
        val added = repository.addIfAbsent(
            occurredOn = row.date,
            type = row.reviewedType ?: importedAttendanceType(row.comment, adjustment),
            points = HalfPoints(kotlin.math.abs(adjustment)),
            status = AttendanceEventStatus.CONFIRMED,
            note = row.comment,
            sourceDocumentId = documentId,
            sourcePageNumber = row.sourcePageNumber,
            importIdentity = row.sourceRowKey?.let { java.util.UUID.nameUUIDFromBytes("$documentId:$it".toByteArray()).toString() },
        )
        if (added) {
            addedCount++
            if (!row.date.isAfter(calculationDate) &&
            (adjustment < 0 || calculationDate.isBefore(row.date.plusMonths(12)))
            ) importedActiveTotal += adjustment
        }
    }
    val openingHalfPoints = setup.attendanceOpeningRemainder.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(2))
        ?.toInt()
        ?: 0
    val manualTotal = setup.currentAttendancePoints.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(2))
        ?.toInt()
    val sheetTotal = parsed.currentTotalHalfPoints ?: manualTotal
    if (sheetTotal != null) {
        require(sheetTotal in -2..10000) { "Reported balance is outside the supported range" }
        val datedPoints = AttendanceCalculator().breakdown(repository.allEvents(), effectiveDate).includedEvents.sumOf { if (it.type == AttendanceEventType.ATTENDANCE_CREDIT) -it.points.value else it.points.value }
        val reconciledOpening = sheetTotal - datedPoints
        return AttendanceImportResult(
            setup = setup.copy(
                currentAttendancePoints = HalfPoints(sheetTotal).asDisplayValue(),
                attendanceAsOfDate = effectiveDate.toString(),
                attendanceOpeningRemainder = displayHalfPoints(reconciledOpening),
            ),
            addedCount = addedCount,
            skippedCount = usableRows.size - addedCount,
        )
    }
    return AttendanceImportResult(
        setup = setup.copy(attendanceOpeningRemainder = displayHalfPoints(openingHalfPoints - importedActiveTotal)),
        addedCount = addedCount,
        skippedCount = usableRows.size - addedCount,
    )
}

private fun isAnnualFalloff(comment: String): Boolean {
    val normalized = comment.lowercase()
    return ("1 year" in normalized || "one year" in normalized || "annual" in normalized) &&
        ("roll" in normalized || "fall" in normalized)
}
