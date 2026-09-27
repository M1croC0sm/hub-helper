package app.hubhelper

import app.hubhelper.domain.*
import org.json.JSONObject
import java.time.LocalDate

/** The only supported wire versions; version 5 has no documented fixture. */
object BackupFormat {
    const val CURRENT_VERSION = 6
    val supportedVersions = setOf(1, 2, 3, 4, 6)
    const val MAX_BYTES = 300L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
    const val MAX_ENTRIES = 10000

    fun validate(manifest: JSONObject) {
        require(manifest.getInt("formatVersion") in supportedVersions) { "Unsupported backup format version" }
        fun rows(key: String, validate: (JSONObject) -> Unit) {
            val rows = manifest.optJSONArray(key) ?: return
            require(rows.length() <= 100000) { "Too many $key records" }
            repeat(rows.length()) { validate(rows.getJSONObject(it)) }
        }
        fun date(row: JSONObject) { LocalDate.parse(row.getString("date")) }
        rows("attendanceEvents") {
            date(it)
            AttendanceEventType.valueOf(it.getString("type"))
            AttendanceEventStatus.valueOf(it.getString("status"))
            require(it.getInt("halfPoints") in 0..10000) { "Invalid attendance points" }
            if (!it.isNull("sourcePageNumber")) require(it.getInt("sourcePageNumber") > 0)
        }
        rows("timeAdjustments") { date(it); TimeBalanceKind.valueOf(it.getString("kind")); it.getInt("minutes") }
        rows("callIns") { date(it); require(it.getInt("ptoMinutes") in 1..1440) }
        rows("holidays") { date(it); require(it.getString("name").isNotBlank()) }
        rows("notes") { date(it); require(it.getString("text").isNotBlank()) }
        rows("bookedPtoDays") {
            date(it); BookedTimeType.valueOf(it.optString("type", "REGULAR_PTO"))
        }
        val documentIds = mutableSetOf<String>()
        val archivePaths = mutableSetOf<String>()
        rows("documents") {
            require(documentIds.add(it.getString("id"))) { "Duplicate document identity" }
            require(archivePaths.add(it.getString("archivePath"))) { "Duplicate document path" }
            DocumentCategory.valueOf(it.getString("category"))
            if (it.has("ocrStatus")) OcrStatus.valueOf(it.getString("ocrStatus"))
            val hash = it.optString("sha256")
            require(hash.isEmpty() || hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid document checksum" }
        }
        manifest.optJSONObject("setup")?.let { setup ->
            listOf("ptoBalanceHours", "sickBalanceHours").forEach { key ->
                setup.optString(key).takeIf(String::isNotBlank)?.let { Minutes.fromHours(it) }
            }
            listOf("currentAttendancePoints", "attendanceOpeningRemainder").forEach { key ->
                setup.optString(key).takeIf(String::isNotBlank)?.let { it.toBigDecimal().multiply(2.toBigDecimal()).intValueExact() }
            }
            listOf("balancesAsOfDate", "hireDate", "paydayAnchor", "attendanceAsOfDate", "shiftEffectiveDate").forEach { key ->
                setup.optString(key).takeIf(String::isNotBlank)?.let(LocalDate::parse)
            }
            setup.optString("shiftPreset").takeIf { it.isNotBlank() && it != "null" }?.let(ShiftPreset::valueOf)
            mapOf("callInsRemaining" to 0..5, "birthdayMonth" to 1..12, "floatingHolidayAllowance" to 0..10,
                "callInsBalanceYear" to 1900..9999).forEach { (key, range) ->
                setup.optString(key).takeIf(String::isNotBlank)?.let { require(it.toInt() in range) { "Invalid $key" } }
            }
        }
    }
}
