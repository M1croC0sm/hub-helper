package app.hubhelper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "business_state")
data class BusinessStateEntity(@PrimaryKey val key: String, val payload: String, val updatedAt: Long)

@Entity(tableName = "audit_entries")
data class AuditEntryEntity(@PrimaryKey val id: String = java.util.UUID.randomUUID().toString(), val kind: String, val payload: String, val recordedAt: Long = System.currentTimeMillis())

@Entity(tableName = "import_receipts")
data class ImportReceiptEntity(@PrimaryKey val digest: String, val importedAt: Long)

@Dao
interface BusinessStateDao {
    @Query("SELECT * FROM business_state WHERE `key` LIKE 'schedule:%' ORDER BY `key`")
    suspend fun schedules(): List<BusinessStateEntity>

    @Query("SELECT * FROM business_state WHERE `key` = :key")
    suspend fun get(key: String): BusinessStateEntity?
    @Query("SELECT * FROM business_state WHERE `key` = :key")
    fun observe(key: String): Flow<BusinessStateEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: BusinessStateEntity)
    @Insert suspend fun audit(value: AuditEntryEntity)
    @Query("SELECT * FROM audit_entries ORDER BY recordedAt DESC")
    suspend fun auditEntries(): List<AuditEntryEntity>
    @Query("SELECT COUNT(*) FROM import_receipts WHERE digest = :digest")
    suspend fun imported(digest: String): Int
    @Insert suspend fun receipt(value: ImportReceiptEntity)
}
