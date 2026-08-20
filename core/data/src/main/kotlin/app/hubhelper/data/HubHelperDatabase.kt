package app.hubhelper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceEventEntity::class, TimeBalanceAdjustmentEntity::class, DocumentEntity::class, WorkNoteEntity::class, HolidayEntity::class, CallInEntity::class, BookedPtoEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class HubHelperDatabase : RoomDatabase() {
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_in_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occurredEpochDay` INTEGER NOT NULL, `ptoMinutes` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `booked_pto_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `sourceDocumentId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL)",
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `booked_pto_days` ADD COLUMN `usageType` TEXT NOT NULL DEFAULT 'REGULAR_PTO'")
            }
        }
    }
}
