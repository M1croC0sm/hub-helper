package app.hubhelper.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "booked_pto_days")
data class BookedPtoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val sourceDocumentId: String?,
    @ColumnInfo(defaultValue = "'REGULAR_PTO'") val usageType: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val durationMinutes: Int = 0,
    @ColumnInfo(defaultValue = "'APPROVED'") val bookingStatus: String = "APPROVED",
    @ColumnInfo(defaultValue = "1") val legacyAssumption: Boolean = true,
    @ColumnInfo(defaultValue = "''") val stableId: String = java.util.UUID.randomUUID().toString(),
)
