package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.SourceReference
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AttendanceRepository internal constructor(private val dao: AttendanceDao) {
    val events: Flow<List<AttendanceEvent>> = dao.observeAll().map { rows -> rows.map(AttendanceEventEntity::toDomain) }

    suspend fun hasSourceDocument(documentId: String): Boolean = dao.countBySourceDocument(documentId) > 0

    suspend fun add(
        occurredOn: LocalDate,
        type: AttendanceEventType,
        points: HalfPoints,
        status: AttendanceEventStatus,
        note: String?,
        sourceDocumentId: String? = null,
    ) {
        dao.insert(
            AttendanceEventEntity(
                occurredEpochDay = occurredOn.toEpochDay(),
                type = type.name,
                halfPoints = points.value,
                status = status.name,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                sourceDocumentId = sourceDocumentId,
                sourcePageNumber = null,
                policyVersion = "Light Industrial Attendance Policy / version unknown",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** Inserts an event unless the same dated event (including its details) already exists. */
    suspend fun addIfAbsent(
        occurredOn: LocalDate,
        type: AttendanceEventType,
        points: HalfPoints,
        status: AttendanceEventStatus,
        note: String?,
        sourceDocumentId: String? = null,
    ): Boolean {
        val normalizedNote = note.canonicalNote()
        val duplicate = dao.findMatching(occurredOn.toEpochDay(), type.name, points.value, status.name)
            .any { it.note.canonicalNote() == normalizedNote }
        if (duplicate) return false
        add(occurredOn, type, points, status, note, sourceDocumentId)
        return true
    }

    suspend fun delete(event: AttendanceEvent) {
        val numericId = event.id.toLongOrNull() ?: return
        dao.delete(event.toEntity(numericId))
    }

    suspend fun update(event: AttendanceEvent) {
        val numericId = event.id.toLongOrNull() ?: return
        dao.update(event.toEntity(numericId).copy(createdAtEpochMillis = System.currentTimeMillis()))
    }

    companion object {
        fun create(context: Context): AttendanceRepository =
            AttendanceRepository(HubHelperDatabase.get(context).attendanceDao())
    }
}

private fun String?.canonicalNote(): String = this.orEmpty()
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

private fun AttendanceEventEntity.toDomain() = AttendanceEvent(
    id = id.toString(),
    occurredOn = LocalDate.ofEpochDay(occurredEpochDay),
    type = AttendanceEventType.valueOf(type),
    points = HalfPoints(halfPoints),
    status = AttendanceEventStatus.valueOf(status),
    source = sourceDocumentId?.let { SourceReference(it, sourcePageNumber, policyVersion) },
    note = note,
)

private fun AttendanceEvent.toEntity(numericId: Long) = AttendanceEventEntity(
    id = numericId,
    occurredEpochDay = occurredOn.toEpochDay(),
    type = type.name,
    halfPoints = points.value,
    status = status.name,
    note = note,
    sourceDocumentId = source?.documentId,
    sourcePageNumber = source?.pageNumber,
    policyVersion = source?.policyVersion,
    createdAtEpochMillis = 0,
)
