package app.hubhelper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceEventEntity::class, TimeBalanceAdjustmentEntity::class, DocumentEntity::class, WorkNoteEntity::class, HolidayEntity::class, CallInEntity::class, BookedPtoEntity::class, BusinessStateEntity::class, AuditEntryEntity::class, ImportReceiptEntity::class, DocumentPageEntity::class, DocumentPageFts::class],
    version = 5,
    exportSchema = true,
)
abstract class HubHelperDatabase : RoomDatabase() {
    abstract fun businessStateDao(): BusinessStateDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun timeBalanceDao(): TimeBalanceDao
    abstract fun documentDao(): DocumentDao
    abstract fun workNoteDao(): WorkNoteDao
    abstract fun holidayDao(): HolidayDao
    abstract fun callInDao(): CallInDao
    abstract fun bookedPtoDao(): BookedPtoDao

    companion object {
        @Volatile private var instance: HubHelperDatabase? = null

        fun get(context: Context): HubHelperDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HubHelperDatabase::class.java,
                "hub-helper.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }


        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `attendance_events` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `attendance_events` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE `time_balance_adjustments` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `time_balance_adjustments` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE `call_in_events` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `call_in_events` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE `booked_pto_days` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `booked_pto_days` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE `work_notes` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `work_notes` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE `plant_holidays` ADD COLUMN `stableId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `plant_holidays` SET `stableId` = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE documents ADD COLUMN originalDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plant_holidays ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER'")
                db.execSQL("ALTER TABLE plant_holidays ADD COLUMN suppressed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE booked_pto_days ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE booked_pto_days ADD COLUMN bookingStatus TEXT NOT NULL DEFAULT 'APPROVED'")
                db.execSQL("ALTER TABLE booked_pto_days ADD COLUMN legacyAssumption INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE call_in_events ADD COLUMN bookingId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE time_balance_adjustments ADD COLUMN bookingId TEXT DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS business_state (`key` TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_entries (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL, recordedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS import_receipts (digest TEXT NOT NULL PRIMARY KEY, importedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS document_pages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, documentId TEXT NOT NULL, pageNumber INTEGER NOT NULL, text TEXT NOT NULL, status TEXT NOT NULL, error TEXT, blocksJson TEXT NOT NULL DEFAULT '[]')")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_document_pages_documentId_pageNumber ON document_pages(documentId, pageNumber)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS document_pages_fts USING FTS4(text TEXT NOT NULL, content=`document_pages`)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_in_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurredEpochDay` INTEGER NOT NULL, `ptoMinutes` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `booked_pto_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `sourceDocumentId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL)",
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `booked_pto_days` ADD COLUMN `usageType` TEXT NOT NULL DEFAULT 'REGULAR_PTO'")
            }
        }
    }
}
