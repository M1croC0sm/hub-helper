package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY importedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insert(document: DocumentEntity)

    @Query("UPDATE documents SET ocrText = :text, ocrStatus = :status WHERE id = :id")
    suspend fun updateOcr(id: String, text: String?, status: String)

    @Delete
    suspend fun delete(document: DocumentEntity)
}

