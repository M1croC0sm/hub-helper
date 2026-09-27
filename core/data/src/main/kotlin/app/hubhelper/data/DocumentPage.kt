package app.hubhelper.data

import androidx.room.*

@Entity(tableName = "document_pages", indices = [Index(value = ["documentId", "pageNumber"], unique = true)])
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val pageNumber: Int,
    val text: String,
    val status: String,
    val error: String?,
    @ColumnInfo(defaultValue = "'[]'") val blocksJson: String = "[]",
)

@Fts4(contentEntity = DocumentPageEntity::class)
@Entity(tableName = "document_pages_fts")
data class DocumentPageFts(val text: String)

@Dao
interface DocumentPageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber")
    suspend fun pages(documentId: String): List<DocumentPageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(page: DocumentPageEntity)
    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun delete(documentId: String)
    @Query("SELECT document_pages.* FROM document_pages JOIN document_pages_fts ON document_pages.id = document_pages_fts.rowid WHERE document_pages_fts MATCH :query LIMIT 100")
    suspend fun search(query: String): List<DocumentPageEntity>
}
