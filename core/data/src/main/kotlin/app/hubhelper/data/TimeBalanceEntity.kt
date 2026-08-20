package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_balance_adjustments")
data class TimeBalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredEpochDay: Long,
    val kind: String,
    val minutes: Int,
    val note: String?,
    val createdAtEpochMillis: Long,
)

