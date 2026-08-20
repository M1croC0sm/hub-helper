package app.hubhelper

import android.content.Context
import android.net.Uri
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.WorkDocument
import app.hubhelper.domain.WorkNote
import app.hubhelper.domain.CallInEvent
import app.hubhelper.domain.BookedPtoDay
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BackupExporter {
    suspend fun export(
        context: Context,
        destination: Uri,
        setup: SetupData,
        events: List<AttendanceEvent>,
        timeAdjustments: List<TimeBalanceAdjustment>,
        holidays: List<PlantHoliday>,
        notes: List<WorkNote>,
        documents: List<WorkDocument>,
        callIns: List<CallInEvent>,
        bookedPtoDays: List<BookedPtoDay>,
    ) = withContext(Dispatchers.IO) {
        val manifest = JSONObject().apply {
            put("formatVersion", 6)
            put("exportedAtEpochMillis", System.currentTimeMillis())
            put("setup", JSONObject().apply {
                put("ptoBalanceHours", setup.ptoBalanceHours)
                put("sickBalanceHours", setup.sickBalanceHours)
                put("currentAttendancePoints", setup.currentAttendancePoints)
                put("attendanceOpeningRemainder", setup.attendanceOpeningRemainder)
                put("shiftPreset", setup.shiftPreset)
                put("hireDate", setup.hireDate)
                put("balancesAsOfDate", setup.balancesAsOfDate)
                put("callInsRemaining", setup.callInsRemaining)
                put("callInsBalanceYear", setup.callInsBalanceYear)
                put("birthdayMonth", setup.birthdayMonth)
            })
            put("attendanceEvents", JSONArray(events.map { event ->
                JSONObject().apply {
                    put("date", event.occurredOn.toString())
                    put("type", event.type.name)
                    put("halfPoints", event.points.value)
                    put("status", event.status.name)
                    put("note", event.note)
                    put("sourceDocumentId", event.source?.documentId)
                    put("sourcePageNumber", event.source?.pageNumber)
                    put("policyVersion", event.source?.policyVersion)
                }
            }))
            put("timeAdjustments", JSONArray(timeAdjustments.map { adjustment ->
                JSONObject().apply {
                    put("date", adjustment.occurredOn.toString())
                    put("kind", adjustment.kind.name)
                    put("minutes", adjustment.minutes)
                    put("note", adjustment.note)
                }
            }))
            put("holidays", JSONArray(holidays.map { holiday ->
                JSONObject().put("date", holiday.date.toString()).put("name", holiday.name)
            }))
            put("notes", JSONArray(notes.map { note ->
                JSONObject().put("date", note.date.toString()).put("text", note.text)
            }))
            put("callIns", JSONArray(callIns.map { event ->
                JSONObject()
                    .put("date", event.occurredOn.toString())
                    .put("ptoMinutes", event.ptoMinutes)
            }))
            put("bookedPtoDays", JSONArray(bookedPtoDays.map { day ->
                JSONObject()
                    .put("date", day.date.toString())
                    .put("sourceDocumentId", day.sourceDocumentId)
                    .put("type", day.type.name)
            }))
            put("documents", JSONArray(documents.map { document ->
                JSONObject().apply {
                    put("id", document.id)
                    put("title", document.title)
                    put("category", document.category.name)
                    put("mimeType", document.mimeType)
                    put("originalName", document.originalName)
                    put("sha256", document.sha256)
                    put("ocrText", document.ocrText)
                    put("ocrStatus", document.ocrStatus.name)
                    put("archivePath", "documents/${document.id}-${safeName(document.originalName)}")
                }
            }))
        }

        val raw = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "Unable to create backup" }
        raw.use {
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray())
                zip.closeEntry()
                documents.forEach { document ->
                    val source = File(document.privatePath)
                    if (source.isFile) {
                        zip.putNextEntry(ZipEntry("documents/${document.id}-${safeName(document.originalName)}"))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
}
