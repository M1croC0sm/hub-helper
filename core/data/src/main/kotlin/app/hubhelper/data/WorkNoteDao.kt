package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkNoteDao {
    @Query("SELECT * FROM work_notes ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<WorkNoteEntity>>

    @Insert
    suspend fun insert(note: WorkNoteEntity)

    @Delete
    suspend fun delete(note: WorkNoteEntity)
}

