package app.hubhelper.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
    @ColumnInfo(defaultValue = "'USER'") val origin: String = "USER",
    @ColumnInfo(defaultValue = "0") val suppressed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val stableId: String = java.util.UUID.randomUUID().toString(),
)

