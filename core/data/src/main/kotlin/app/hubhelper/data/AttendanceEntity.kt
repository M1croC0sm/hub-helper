package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_events")
data class AttendanceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredEpochDay: Long,
    val type: String,
    val halfPoints: Int,
    val status: String,
    val note: String?,
    val sourceDocumentId: String?,
    val sourcePageNumber: Int?,
    val policyVersion: String?,
    val createdAtEpochMillis: Long,
)

