package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_events ORDER BY occurredEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<AttendanceEventEntity>>

    @Query("SELECT COUNT(*) FROM attendance_events WHERE sourceDocumentId = :documentId")
    suspend fun countBySourceDocument(documentId: String): Int

    @Query("SELECT * FROM attendance_events WHERE occurredEpochDay = :occurredEpochDay AND type = :type AND halfPoints = :halfPoints AND status = :status")
    suspend fun findMatching(
        occurredEpochDay: Long,
        type: String,
        halfPoints: Int,
        status: String,
    ): List<AttendanceEventEntity>

    @Insert
    suspend fun insert(event: AttendanceEventEntity): Long

    @Update
    suspend fun update(event: AttendanceEventEntity)

    @Delete
    suspend fun delete(event: AttendanceEventEntity)
}
