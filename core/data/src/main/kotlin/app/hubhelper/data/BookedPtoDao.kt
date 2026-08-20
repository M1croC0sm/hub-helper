package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookedPtoDao {
    @Query("SELECT * FROM booked_pto_days ORDER BY dateEpochDay, id")
    fun observeAll(): Flow<List<BookedPtoEntity>>

    @Query("SELECT COUNT(*) FROM booked_pto_days WHERE dateEpochDay = :dateEpochDay AND ((sourceDocumentId = :sourceDocumentId) OR (sourceDocumentId IS NULL AND :sourceDocumentId IS NULL))")
    suspend fun count(dateEpochDay: Long, sourceDocumentId: String?): Int

    @Insert
    suspend fun insert(day: BookedPtoEntity): Long

    @Delete
    suspend fun delete(day: BookedPtoEntity)
}
