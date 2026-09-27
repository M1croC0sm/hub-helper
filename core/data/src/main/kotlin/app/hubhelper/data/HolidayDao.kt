package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @androidx.room.Update suspend fun update(holiday: HolidayEntity)

    @Query("SELECT * FROM plant_holidays WHERE stableId = :stableId LIMIT 1")
    suspend fun byStableId(stableId: String): HolidayEntity?

    @Query("SELECT * FROM plant_holidays")
    suspend fun getAll(): List<HolidayEntity>

    @Query("SELECT * FROM plant_holidays WHERE suppressed = 0 ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT COUNT(*) FROM plant_holidays WHERE dateEpochDay = :dateEpochDay AND lower(name) = lower(:name)")
    suspend fun count(dateEpochDay: Long, name: String): Int

    @Insert
    suspend fun insert(holiday: HolidayEntity)

    @Delete
    suspend fun delete(holiday: HolidayEntity)
}
