package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_in_events")
data class CallInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredEpochDay: Long,
    val ptoMinutes: Int,
    val createdAtEpochMillis: Long,
)
