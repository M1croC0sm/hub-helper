package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plant_holidays")
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val name: String,
)

