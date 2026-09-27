package app.hubhelper

import android.content.Context
import android.net.Uri
import app.hubhelper.data.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Version 7 contains database rows, stable record IDs and original files. */
object DatabaseBackup {
    val tables = listOf("business_state", "audit_entries", "import_receipts", "attendance_events", "time_balance_adjustments",
        "call_in_events", "booked_pto_days", "work_notes", "plant_holidays", "documents", "document_pages")
    private val autoIds = setOf("attendance_events", "time_balance_adjustments", "call_in_events", "booked_pto_days", "work_notes", "plant_holidays", "document_pages")

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        StorageCoordinator.mutex.withLock {
            val database = HubHelperDatabase.get(context)
            val manifest = database.withTransaction {
                JSONObject().put("formatVersion", 7).put("schemaVersion", 5).put("tables", JSONObject().apply {
                    tables.forEach { table ->
                        val rows = JSONArray()
                        database.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                            while (cursor.moveToNext()) rows.put(JSONObject().apply {
                                cursor.columnNames.forEachIndexed { index, name ->
                                    put(name, when (cursor.getType(index)) {
                                        android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                        else -> cursor.getString(index)
                                    })
                                }
                            })
                        }
                        put(table, rows)
                    }
                })
            }
            val docs = manifest.getJSONObject("tables").getJSONArray("documents")
            repeat(docs.length()) { i ->
                val doc = docs.getJSONObject(i)
                val original = File(doc.getString("privatePath"))
                if (doc.optInt("originalDeleted") == 0) require(original.isFile && sha256(original).equals(doc.getString("sha256"), true)) { "Original missing or changed: ${doc.getString("title")}" }
                doc.put("archivePath", "documents/${doc.getString("id")}")
                doc.remove("privatePath")
            }
            val manifestBytes = manifest.toString().toByteArray()
            require(manifestBytes.size <= BackupFormat.MAX_MANIFEST_BYTES) { "Backup manifest exceeds 16 MB" }
            val originalsSize = database.documentDao().getAll().filterNot { it.originalDeleted }.sumOf { File(it.privatePath).length() }
            require(originalsSize + manifestBytes.size <= BackupFormat.MAX_BYTES && docs.length() < BackupFormat.MAX_ENTRIES) { "Backup exceeds the supported 300 MB archive limit" }
            val staging = File.createTempFile("verified-backup-", ".zip", context.cacheDir)
            try {
                ZipOutputStream(staging.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifestBytes); zip.closeEntry()
                    val originals = database.documentDao().getAll().associateBy { it.id }
                    repeat(docs.length()) { i ->
                        val doc = docs.getJSONObject(i)
                        if (doc.optInt("originalDeleted") != 0) return@repeat
                        val source = requireNotNull(originals[doc.getString("id")])
                        zip.putNextEntry(ZipEntry(doc.getString("archivePath")))
                        File(source.privatePath).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
                // Read every compressed entry before publishing to detect I/O/CRC failures.
                ZipInputStream(staging.inputStream()).use { zip ->
                    val buffer = ByteArray(8192)
                    while (zip.nextEntry != null) { while (zip.read(buffer) >= 0) { }; zip.closeEntry() }
                }
                requireNotNull(context.contentResolver.openOutputStream(destination, "w")).use { out -> staging.inputStream().use { it.copyTo(out) } }
                context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).edit().putLong("last_success", System.currentTimeMillis()).apply()
            } finally { staging.delete() }
        }
    }

    suspend fun restore(context: Context, manifest: JSONObject, files: Map<String, File>, replace: Boolean): BackupImportResult {
        require(manifest.getInt("schemaVersion") == 5) { "Unsupported database backup schema" }
        val database = HubHelperDatabase.get(context)
        val data = manifest.getJSONObject("tables")
        require(data.keys().asSequence().toSet() == tables.toSet()) { "Incomplete or unknown backup tables" }
        val docs = data.getJSONArray("documents")
        // Validate all columns against the live schema before modifying persistent data.
        tables.forEach { table ->
            val columns = mutableSetOf<String>()
            database.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) columns.add(c.getString(1)) }
            val rows = data.getJSONArray(table)
            require(rows.length() <= 100000) { "Too many records" }
            repeat(rows.length()) { i ->
                val keys = rows.getJSONObject(i).keys().asSequence().toSet()
                val allowed = if (table == "documents") columns - "privatePath" + "archivePath" else columns
                require(keys == allowed) { "Invalid columns in $table" }
            }
        }
        fun rows(table: String, action: (JSONObject) -> Unit) {
            val values = data.getJSONArray(table)
            val identities = mutableSetOf<String>()
            repeat(values.length()) { i ->
                val row = values.getJSONObject(i)
                val identity = when { row.has("stableId") -> row.getString("stableId"); row.has("key") -> row.getString("key"); row.has("digest") -> row.getString("digest"); else -> row.get("id").toString() }
                require(identity.isNotBlank() && identities.add(identity)) { "Duplicate or blank identity in $table" }
                action(row)
            }
        }
        rows("attendance_events") { row ->
            java.time.LocalDate.ofEpochDay(row.getLong("occurredEpochDay"))
            app.hubhelper.domain.AttendanceEventType.valueOf(row.getString("type"))
            app.hubhelper.domain.AttendanceEventStatus.valueOf(row.getString("status"))
            require(row.getLong("halfPoints") in 0..10000)
        }
        rows("time_balance_adjustments") { row ->
            java.time.LocalDate.ofEpochDay(row.getLong("occurredEpochDay"))
            app.hubhelper.domain.TimeBalanceKind.valueOf(row.getString("kind"))
            require(row.getLong("minutes") in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        }
        rows("booked_pto_days") { row ->
            java.time.LocalDate.ofEpochDay(row.getLong("dateEpochDay"))
            app.hubhelper.domain.BookedTimeType.valueOf(row.getString("usageType"))
            app.hubhelper.domain.BookingStatus.valueOf(row.getString("bookingStatus"))
            require(row.getLong("durationMinutes") in 1..1440 || (row.getLong("durationMinutes") == 0L && row.getInt("legacyAssumption") == 1))
        }
        rows("call_in_events") { row -> java.time.LocalDate.ofEpochDay(row.getLong("occurredEpochDay")); require(row.getLong("ptoMinutes") in 1..1440) }
        rows("work_notes") { row -> java.time.LocalDate.ofEpochDay(row.getLong("dateEpochDay")); require(row.getString("text").isNotBlank()) }
        rows("plant_holidays") { row -> java.time.LocalDate.ofEpochDay(row.getLong("dateEpochDay")); require(row.getString("name").isNotBlank()) }
        rows("documents") { row ->
            app.hubhelper.domain.DocumentCategory.valueOf(row.getString("category"))
            app.hubhelper.domain.OcrStatus.valueOf(row.getString("ocrStatus"))
            require(row.getInt("originalDeleted") in 0..1)
        }
        rows("document_pages") { row -> require(row.getInt("pageNumber") in 1..DocumentPages.MAX_PAGES) }
        rows("business_state") { row ->
            val key = row.getString("key")
            if (key.startsWith("schedule:")) {
                app.hubhelper.domain.ShiftPreset.valueOf(row.getString("payload"))
                if (key != "schedule:unknown") java.time.LocalDate.parse(key.removePrefix("schedule:"))
            }
        }
        rows("audit_entries") { }
        rows("import_receipts") { }
        val sourceIds = mutableSetOf<String>()
        repeat(docs.length()) { i ->
            val doc = docs.getJSONObject(i)
            require(sourceIds.add(doc.getString("id"))) { "Duplicate document ID" }
            if (doc.optInt("originalDeleted") != 0) return@repeat
            val original = requireNotNull(files[doc.getString("archivePath")]) { "Missing original" }
            require(sha256(original).equals(doc.getString("sha256"), true)) { "Original checksum mismatch" }
        }
        data.getJSONArray("business_state").let { rows -> repeat(rows.length()) { i ->
            val row = rows.getJSONObject(i)
            if (row.getString("key") == "setup") BackupFormat.validate(JSONObject().put("formatVersion", 6).put("setup", JSONObject(row.getString("payload"))))
        } }
        val journal = RestoreJournal(context)
        val created = mutableListOf<File>()
        val previousFiles = database.documentDao().getAll().map { it.privatePath }
        try {
            // Every new file is journaled before creation. No existing original is overwritten.
            repeat(docs.length()) { i ->
                val doc = docs.getJSONObject(i)
                val target = File(context.filesDir, "documents/${UUID.randomUUID()}.original")
                created += target
                journal.write(JSONArray(created.map { it.absolutePath }))
                target.parentFile!!.mkdirs()
                if (doc.optInt("originalDeleted") == 0) requireNotNull(files[doc.getString("archivePath")]).copyTo(target)
                doc.put("privatePath", target.absolutePath).remove("archivePath")
            }
            database.withTransaction {
                val sql = database.openHelper.writableDatabase
                if (replace) tables.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
                tables.forEach { table ->
                    val rows = data.getJSONArray(table)
                    repeat(rows.length()) { i ->
                        val row = rows.getJSONObject(i)
                        val identity = when {
                            table == "business_state" -> "key"
                            table == "import_receipts" -> "digest"
                            row.has("stableId") -> "stableId"
                            else -> "id"
                        }
                        val where = if (table == "document_pages") "documentId = ? AND pageNumber = ?" else "`$identity` = ?"
                        val args: Array<Any> = if (table == "document_pages") arrayOf(row.getString("documentId"), row.getInt("pageNumber")) else arrayOf(row.get(identity))
                        val existing = sql.query("SELECT * FROM `$table` WHERE $where", args).use { c ->
                            if (!c.moveToFirst()) null else JSONObject().apply { c.columnNames.forEachIndexed { n, key -> put(key, if(c.isNull(n)) JSONObject.NULL else c.getString(n)) } }
                        }
                        if (existing != null) {
                            val ignored = setOf("id", "privatePath", "updatedAt", "importedAt", "recordedAt")
                            val same = row.keys().asSequence().filterNot { it in ignored }.all { key -> row.opt(key)?.toString() == existing.opt(key)?.toString() }
                            require(same) { "Conflicting $table record. Use Replace to restore this snapshot, or keep current records." }
                        } else {
                            val values = android.content.ContentValues()
                            row.keys().forEach { key ->
                                if (!(table in autoIds && key == "id")) {
                                    val value = row.get(key)
                                    when (value) { JSONObject.NULL -> values.putNull(key); is Number -> values.put(key, value.toLong()); else -> values.put(key, value.toString()) }
                                }
                            }
                            sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                        }
                    }
                }
            }
            val kept = database.documentDao().getAll().map { it.privatePath }.toSet()
            created.filter { it.absolutePath !in kept }.forEach { it.delete() }
            if (replace) previousFiles.filterNot { it in kept }.forEach { File(it).delete() }
            journal.delete()
            return BackupImportResult(SetupStore(database).read() ?: SetupData(), data.getJSONArray("attendance_events").length(),
                data.getJSONArray("time_balance_adjustments").length(), data.getJSONArray("plant_holidays").length(),
                data.getJSONArray("work_notes").length(), docs.length(), data.getJSONArray("call_in_events").length(), data.getJSONArray("booked_pto_days").length())
        } catch (error: Throwable) {
            recover(context)
            throw error
        }
    }

    suspend fun recover(context: Context) {
        val journal = RestoreJournal(context)
        val kept = HubHelperDatabase.get(context).documentDao().getAll().map { it.privatePath }.toSet()
        val paths = journal.read()
        repeat(paths.length()) { i ->
            val file = File(paths.getString(i))
            if (file.canonicalFile.parentFile == File(context.filesDir, "documents").canonicalFile && file.absolutePath !in kept) file.delete()
        }
        journal.delete()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
