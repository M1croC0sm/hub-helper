package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallInDao {
    @Query("SELECT * FROM call_in_events WHERE stableId = :stableId LIMIT 1")
    suspend fun byStableId(stableId: String): CallInEntity?

    @Query("SELECT * FROM call_in_events")
    suspend fun getAll(): List<CallInEntity>

    @Query("SELECT * FROM call_in_events ORDER BY occurredEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<CallInEntity>>

    @Insert
    suspend fun insert(event: CallInEntity): Long

    @Delete
    suspend fun delete(event: CallInEntity)
}
