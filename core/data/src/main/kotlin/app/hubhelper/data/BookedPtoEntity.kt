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
)
