package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.SourceReference
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AttendanceRepository internal constructor(private val dao: AttendanceDao, private val database: HubHelperDatabase) {
    val events: Flow<List<AttendanceEvent>> = dao.observeAll().map { rows -> rows.map(AttendanceEventEntity::toDomain) }

    suspend fun allEvents(): List<AttendanceEvent> = dao.getAll().map(AttendanceEventEntity::toDomain)

    /** Removes only normalized duplicates, retaining distinct same-day details. */
    suspend fun removeDuplicateEvents(): Int {
        val seen = mutableSetOf<String>()
        var removed = 0
        allEvents().sortedBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }.forEach { event ->
            val key = listOf(event.occurredOn, event.type, event.points.value, event.status, event.note.canonicalNote()).joinToString("|")
            if (!seen.add(key)) {
                event.id.toLongOrNull()?.let { dao.deleteById(it); removed++ }
            }
        }
        return removed
    }

    suspend fun hasSourceDocument(documentId: String): Boolean = dao.countBySourceDocument(documentId) > 0

    suspend fun add(
        occurredOn: LocalDate,
        type: AttendanceEventType,
        points: HalfPoints,
        status: AttendanceEventStatus,
        note: String?,
        sourceDocumentId: String? = null,
        sourcePageNumber: Int? = null,
        policyVersion: String? = null,
        stableId: String = java.util.UUID.randomUUID().toString(),
    ) {
        dao.insert(
            AttendanceEventEntity(
                stableId = stableId,
                occurredEpochDay = occurredOn.toEpochDay(),
                type = type.name,
                halfPoints = points.value,
                status = status.name,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                sourceDocumentId = sourceDocumentId,
                sourcePageNumber = sourcePageNumber,
                policyVersion = policyVersion,
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
        sourcePageNumber: Int? = null,
        importIdentity: String? = null,
    ): Boolean {
        if (importIdentity != null) {
            val existing = dao.byStableId(importIdentity)
            if (existing != null) {
                require(existing.occurredEpochDay == occurredOn.toEpochDay() && existing.halfPoints == points.value && existing.type == type.name && existing.status == status.name) { "A previously reviewed row changed. Correct its existing attendance event instead." }
                return false
            }
            add(occurredOn, type, points, status, note, sourceDocumentId, sourcePageNumber, stableId = importIdentity)
            return true
        }
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

    suspend fun update(event: AttendanceEvent) = database.withTransaction {
        val numericId = event.id.toLongOrNull() ?: return@withTransaction
        val existing = dao.getAll().firstOrNull { it.id == numericId } ?: return@withTransaction
        dao.update(event.toEntity(numericId).copy(createdAtEpochMillis = existing.createdAtEpochMillis, stableId = existing.stableId))
        database.businessStateDao().audit(AuditEntryEntity(kind = "attendance correction", payload = "${existing.stableId}: ${existing.status}/${existing.halfPoints} -> ${event.status}/${event.points.value}"))
    }

    companion object {
        fun create(context: Context): AttendanceRepository =
            AttendanceRepository(HubHelperDatabase.get(context).attendanceDao(), HubHelperDatabase.get(context))
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
