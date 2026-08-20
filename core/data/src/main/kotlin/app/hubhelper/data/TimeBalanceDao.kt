package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeBalanceDao {
    @Query("SELECT * FROM time_balance_adjustments ORDER BY occurredEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TimeBalanceAdjustmentEntity>>

    @Insert
    suspend fun insert(adjustment: TimeBalanceAdjustmentEntity): Long

    @Delete
    suspend fun delete(adjustment: TimeBalanceAdjustmentEntity)
}

