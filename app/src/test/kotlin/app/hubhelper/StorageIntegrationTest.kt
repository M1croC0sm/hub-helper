package app.hubhelper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.room.withTransaction
import app.hubhelper.data.*
import app.hubhelper.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28])
class StorageIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: HubHelperDatabase
    @Before fun setup(): Unit = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        context = ApplicationProvider.getApplicationContext()
        db = HubHelperDatabase.get(context)
        db.clearAllTables()
        File(context.filesDir, "documents").deleteRecursively()
    }
    @Test fun `setup migration is idempotent and retains exact legacy values`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val legacy = SetupPreferences(context)
        legacy.save(SetupData(ptoBalanceHours = "40.25", paydayAnchor = "2026-09-04"))
        val store = SetupStore(db)
        store.initialize(legacy); store.initialize(legacy)
        assertEquals("40.25", store.read()!!.ptoBalanceHours)
        assertEquals(1, db.businessStateDao().auditEntries().size)
    }
    @Test fun `call in failure rolls back allowance with event`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        SetupStore(db).save(SetupData(callInsRemaining = "0", callInsBalanceYear = "2026"))
        try { CallInRepository.create(context).add(LocalDate.of(2026, 9, 1), 480); fail("Must reject") } catch (_: IllegalArgumentException) { }
        assertEquals(0, db.callInDao().getAll().size)
        assertEquals("0", SetupStore(db).read()!!.callInsRemaining)
    }
    @Test fun `deleting a call in twice restores allowance only once`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        SetupStore(db).save(SetupData(callInsRemaining = "5", callInsBalanceYear = "2026"))
        val repository = CallInRepository.create(context)
        val date = LocalDate.of(2026, 9, 1)
        repository.add(date, 480)
        repository.add(date.plusDays(1), 480)
        val stored = db.callInDao().getAll().first()
        val event = CallInEvent(stored.id.toString(), date, 480)
        repository.delete(event); repository.delete(event)
        assertEquals("4", SetupStore(db).read()!!.callInsRemaining)
        assertEquals(1, db.callInDao().getAll().size)
    }
    @Test fun `page full text index follows insert and deletion`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        db.documentPageDao().put(DocumentPageEntity(documentId = "doc", pageNumber = 2, text = "vacation balance", status = "COMPLETE", error = null))
        assertEquals(2, db.documentPageDao().search("vacation").single().pageNumber)
        db.documentPageDao().delete("doc")
        assertTrue(db.documentPageDao().search("vacation").isEmpty())
    }
    @Test fun `backup restore is repeatable and missing original leaves ledger intact`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        SetupStore(db).save(SetupData(ptoBalanceHours = "40", paydayAnchor = "2026-09-04"))
        AttendanceRepository.create(context).add(LocalDate.of(2026, 9, 1), AttendanceEventType.TARDY, HalfPoints(1), AttendanceEventStatus.CONFIRMED, "synthetic")
        val original = File(context.filesDir, "documents/sample.txt").apply { parentFile!!.mkdirs(); writeText("synthetic evidence") }
        db.documentDao().insert(DocumentEntity("doc", "Sample", "OTHER", "text/plain", "sample.txt", original.absolutePath, 0, DatabaseBackup.sha256(original), null, "UNSUPPORTED"))
        val file = File(context.cacheDir, "test-backup.zip")
        DatabaseBackup.export(context, android.net.Uri.fromFile(file))
        val entries = mutableMapOf<String, File>()
        val manifest = ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { it.name != "manifest.json" }.forEach { entry ->
                entries[entry.name] = File.createTempFile("fixture", ".bin", context.cacheDir).apply { zip.getInputStream(entry).use { input -> outputStream().use { input.copyTo(it) } } }
            }
            JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText())
        }
        db.clearAllTables()
        DatabaseBackup.restore(context, JSONObject(manifest.toString()), entries, false)
        DatabaseBackup.restore(context, JSONObject(manifest.toString()), entries, false)
        assertEquals(1, db.attendanceDao().getAll().size)
        assertEquals("2026-09-04", SetupStore(db).read()!!.paydayAnchor)
        assertEquals(1, db.documentDao().getAll().size)
        try { DatabaseBackup.restore(context, JSONObject(manifest.toString()), emptyMap(), true); fail("Missing file must reject") } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.attendanceDao().getAll().size)
    }
    @Test fun `version four database migrates without losing records`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        for (version in 1..4) {
        val name = "migration-test-$version.db"
        context.deleteDatabase(name)
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
        val schema = JSONObject(File("../core/data/schemas/app.hubhelper.data.HubHelperDatabase/$version.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        repeat(entities.length()) { i ->
            val entity = entities.getJSONObject(i)
            old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        old.execSQL("INSERT INTO attendance_events (id, occurredEpochDay, type, halfPoints, status, note, sourceDocumentId, sourcePageNumber, policyVersion, createdAtEpochMillis) VALUES(1, 20000, 'TARDY', 1, 'CONFIRMED', NULL, NULL, NULL, NULL, 100)")
        old.version = version; old.close()
        val migrated = Room.databaseBuilder(context, HubHelperDatabase::class.java, name).addMigrations(HubHelperDatabase.MIGRATION_1_2, HubHelperDatabase.MIGRATION_2_3, HubHelperDatabase.MIGRATION_3_4, HubHelperDatabase.MIGRATION_4_5).build()
        assertEquals(1, migrated.attendanceDao().getAll().single().halfPoints)
        assertTrue(migrated.attendanceDao().getAll().single().stableId.isNotBlank())
        migrated.documentPageDao().put(DocumentPageEntity(documentId = "doc", pageNumber = 1, text = "migration", status = "COMPLETE", error = null))
        assertEquals(1, migrated.documentPageDao().search("migration").size)
        migrated.close()
        }
    }
    @Test fun `restore journal removes orphan but keeps committed original`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val kept = File(context.filesDir, "documents/kept.bin").apply { parentFile!!.mkdirs(); writeText("kept") }
        val orphan = File(context.filesDir, "documents/orphan.bin").apply { writeText("orphan") }
        db.documentDao().insert(DocumentEntity("kept", "Kept", "OTHER", "text/plain", "kept.bin", kept.absolutePath, 0, DatabaseBackup.sha256(kept), null, "UNSUPPORTED"))
        File(context.filesDir, "restore-journal.json").writeText(JSONArray(listOf(kept.absolutePath, orphan.absolutePath)).toString())
        DatabaseBackup.recover(context)
        assertTrue(kept.isFile)
        assertFalse(orphan.exists())
    }

    @Test fun `statement balance covers end of day and later events change total`() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val repository = AttendanceRepository.create(context)
        repository.add(LocalDate.of(2026, 9, 2), AttendanceEventType.TARDY, HalfPoints(2), AttendanceEventStatus.CONFIRMED, null)
        val statement = ParsedAttendanceStatement(listOf(ParsedAttendanceRow(LocalDate.of(2026, 9, 1), "absence", 2, 4, 1, "row-1")), 4, emptyList(), LocalDate.of(2026, 9, 1))
        val result = db.withTransaction {
            applyAttendanceStatement(SetupData(), statement, "source", LocalDate.of(2026, 9, 2), repository).also { SetupStore(db).save(it.setup) }
        }
        val opening = HalfPoints(result.setup.attendanceOpeningRemainder.toBigDecimal().multiply(2.toBigDecimal()).intValueExact())
        assertEquals(4, AttendanceCalculator().totalWithOpening(repository.allEvents(), LocalDate.of(2026, 9, 1), opening).value)
        assertEquals(6, AttendanceCalculator().totalWithOpening(repository.allEvents(), LocalDate.of(2026, 9, 2), opening).value)
        val again = applyAttendanceStatement(result.setup, statement, "source", LocalDate.of(2026, 9, 2), repository)
        assertEquals(0, again.addedCount)
        assertEquals(2, repository.allEvents().size)
    }

}
