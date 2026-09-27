package app.hubhelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("UPDATE documents SET originalDeleted = 1, ocrText = NULL, ocrStatus = 'UNSUPPORTED' WHERE id = :id")
    suspend fun markDeleted(id: String)
    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY importedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insert(document: DocumentEntity)

    @Query("UPDATE documents SET ocrText = :text, ocrStatus = :status WHERE id = :id")
    suspend fun updateOcr(id: String, text: String?, status: String)

    @Delete
    suspend fun delete(document: DocumentEntity)
}

