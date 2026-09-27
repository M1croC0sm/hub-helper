package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeBalanceDao {
    @Query("SELECT * FROM time_balance_adjustments WHERE stableId = :stableId LIMIT 1")
    suspend fun byStableId(stableId: String): TimeBalanceAdjustmentEntity?

    @Query("SELECT * FROM time_balance_adjustments")
    suspend fun getAll(): List<TimeBalanceAdjustmentEntity>

    @Query("SELECT * FROM time_balance_adjustments ORDER BY occurredEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TimeBalanceAdjustmentEntity>>

    @Insert
    suspend fun insert(adjustment: TimeBalanceAdjustmentEntity): Long

    @Delete
    suspend fun delete(adjustment: TimeBalanceAdjustmentEntity)
}

