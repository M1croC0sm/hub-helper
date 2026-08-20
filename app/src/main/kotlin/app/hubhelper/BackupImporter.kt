package app.hubhelper

import android.content.Context
import android.net.Uri
import app.hubhelper.data.AttendanceRepository
import app.hubhelper.data.DocumentRepository
import app.hubhelper.data.HolidayRepository
import app.hubhelper.data.TimeBalanceRepository
import app.hubhelper.data.WorkNoteRepository
import app.hubhelper.data.CallInRepository
import app.hubhelper.data.BookedPtoRepository
import app.hubhelper.domain.BookedTimeType
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.OcrStatus
import app.hubhelper.domain.TimeBalanceKind
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BackupImportResult(
    val setup: SetupData,
    val attendanceCount: Int,
    val timeCount: Int,
    val holidayCount: Int,
    val noteCount: Int,
    val documentCount: Int,
    val callInCount: Int,
    val bookedPtoCount: Int,
)

object BackupImporter {
    private const val MAX_ARCHIVE_BYTES = 300L * 1024L * 1024L

    suspend fun import(
        context: Context,
        source: Uri,
        currentSetup: SetupData,
        attendanceRepository: AttendanceRepository,
        timeBalanceRepository: TimeBalanceRepository,
        holidayRepository: HolidayRepository,
        workNoteRepository: WorkNoteRepository,
        documentRepository: DocumentRepository,
        callInRepository: CallInRepository,
        bookedPtoRepository: BookedPtoRepository,
    ): BackupImportResult = withContext(Dispatchers.IO) {
        val tempDirectory = File(context.cacheDir, "backup-import/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val entries = mutableMapOf<String, File>()
            var manifestText: String? = null
            var totalBytes = 0L
            val input = requireNotNull(context.contentResolver.openInputStream(source)) { "Unable to open backup" }
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(!entry.isDirectory) { "Backup contains an unexpected directory entry" }
                    val destination = File(tempDirectory, UUID.randomUUID().toString())
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_ARCHIVE_BYTES) { "Backup is larger than 300 MB" }
                            output.write(buffer, 0, read)
                        }
                    }
                    if (entry.name == "manifest.json") manifestText = destination.readText() else entries[entry.name] = destination
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val manifest = JSONObject(requireNotNull(manifestText) { "Backup does not contain manifest.json" })
            val formatVersion = manifest.optInt("formatVersion", 0)
            require(formatVersion in 1..4) { "Unsupported backup format version: $formatVersion" }

            val documentIdMap = mutableMapOf<String, String>()
            val documents = manifest.optJSONArray("documents")
            for (index in 0 until (documents?.length() ?: 0)) {
                val value = documents!!.getJSONObject(index)
                val archivePath = value.getString("archivePath")
                val archivedFile = requireNotNull(entries[archivePath]) { "Backup is missing $archivePath" }
                val restored = documentRepository.restore(
                    title = value.optString("title", value.optString("originalName", "Restored document")),
                    category = enumValueOrDefault(value.optString("category"), DocumentCategory.OTHER),
                    mimeType = value.optString("mimeType", "application/octet-stream"),
                    originalName = value.optString("originalName", "restored-document.bin"),
                    expectedSha256 = value.optString("sha256", ""),
                    ocrText = value.nullableString("ocrText"),
                    ocrStatus = enumValueOrDefault(value.optString("ocrStatus"), if (value.isNull("ocrText")) OcrStatus.NOT_STARTED else OcrStatus.COMPLETE),
                    archivedFile = archivedFile,
                )
                documentIdMap[value.optString("id")] = restored.id
            }

            val attendance = manifest.optJSONArray("attendanceEvents")
            for (index in 0 until (attendance?.length() ?: 0)) {
                val value = attendance!!.getJSONObject(index)
                val oldSource = value.nullableString("sourceDocumentId")
                attendanceRepository.add(
                    occurredOn = LocalDate.parse(value.getString("date")),
                    type = AttendanceEventType.valueOf(value.getString("type")),
                    points = HalfPoints(value.getInt("halfPoints")),
                    status = AttendanceEventStatus.valueOf(value.getString("status")),
                    note = value.nullableString("note"),
                    sourceDocumentId = oldSource?.let(documentIdMap::get),
                )
            }

            val time = manifest.optJSONArray("timeAdjustments")
            for (index in 0 until (time?.length() ?: 0)) {
                val value = time!!.getJSONObject(index)
                timeBalanceRepository.add(
                    LocalDate.parse(value.getString("date")),
                    TimeBalanceKind.valueOf(value.getString("kind")),
                    value.getInt("minutes"),
                    value.nullableString("note"),
                )
            }

            val holidays = manifest.optJSONArray("holidays")
            for (index in 0 until (holidays?.length() ?: 0)) {
                val value = holidays!!.getJSONObject(index)
                holidayRepository.add(LocalDate.parse(value.getString("date")), value.getString("name"))
            }

            val notes = manifest.optJSONArray("notes")
            for (index in 0 until (notes?.length() ?: 0)) {
                val value = notes!!.getJSONObject(index)
                workNoteRepository.add(LocalDate.parse(value.getString("date")), value.getString("text"))
            }

            val callIns = manifest.optJSONArray("callIns")
            for (index in 0 until (callIns?.length() ?: 0)) {
                val value = callIns!!.getJSONObject(index)
                callInRepository.add(LocalDate.parse(value.getString("date")), value.getInt("ptoMinutes"))
            }

            val bookedPtoDays = manifest.optJSONArray("bookedPtoDays")
            for (index in 0 until (bookedPtoDays?.length() ?: 0)) {
                val value = bookedPtoDays!!.getJSONObject(index)
                val oldSource = value.nullableString("sourceDocumentId")
                bookedPtoRepository.add(
                    LocalDate.parse(value.getString("date")),
                    oldSource?.let(documentIdMap::get),
                    runCatching { BookedTimeType.valueOf(value.optString("type", BookedTimeType.REGULAR_PTO.name)) }.getOrDefault(BookedTimeType.REGULAR_PTO),
                )
            }

            val setupJson = manifest.optJSONObject("setup")
            val restoredSetup = if (setupJson == null) currentSetup else currentSetup.copy(
                ptoBalanceHours = setupJson.optString("ptoBalanceHours", currentSetup.ptoBalanceHours),
                sickBalanceHours = setupJson.optString("sickBalanceHours", currentSetup.sickBalanceHours),
                currentAttendancePoints = setupJson.optString("currentAttendancePoints", currentSetup.currentAttendancePoints),
                attendanceOpeningRemainder = setupJson.optString("attendanceOpeningRemainder", currentSetup.attendanceOpeningRemainder),
                shiftPreset = setupJson.nullableString("shiftPreset") ?: currentSetup.shiftPreset,
                hireDate = setupJson.optString("hireDate", currentSetup.hireDate),
                balancesAsOfDate = setupJson.optString("balancesAsOfDate", currentSetup.balancesAsOfDate),
                callInsRemaining = setupJson.optString("callInsRemaining", currentSetup.callInsRemaining),
                callInsBalanceYear = setupJson.optString("callInsBalanceYear", currentSetup.callInsBalanceYear),
                birthdayMonth = setupJson.optString("birthdayMonth", currentSetup.birthdayMonth),
            )

            BackupImportResult(
                setup = restoredSetup,
                attendanceCount = attendance?.length() ?: 0,
                timeCount = time?.length() ?: 0,
                holidayCount = holidays?.length() ?: 0,
                noteCount = notes?.length() ?: 0,
                documentCount = documents?.length() ?: 0,
                callInCount = callIns?.length() ?: 0,
                bookedPtoCount = bookedPtoDays?.length() ?: 0,
            )
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf(String::isNotBlank)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
