package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkNoteDao {
    @Query("UPDATE work_notes SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("SELECT * FROM work_notes WHERE stableId = :stableId LIMIT 1")
    suspend fun byStableId(stableId: String): WorkNoteEntity?

    @Query("SELECT * FROM work_notes")
    suspend fun getAll(): List<WorkNoteEntity>

    @Query("SELECT * FROM work_notes ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<WorkNoteEntity>>

    @Insert
    suspend fun insert(note: WorkNoteEntity)

    @Delete
    suspend fun delete(note: WorkNoteEntity)
}

