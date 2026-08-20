package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT * FROM plant_holidays ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT COUNT(*) FROM plant_holidays WHERE dateEpochDay = :dateEpochDay AND lower(name) = lower(:name)")
    suspend fun count(dateEpochDay: Long, name: String): Int

    @Insert
    suspend fun insert(holiday: HolidayEntity)

    @Delete
    suspend fun delete(holiday: HolidayEntity)
}
