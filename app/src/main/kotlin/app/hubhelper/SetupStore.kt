package app.hubhelper

import app.hubhelper.data.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Business setup lives with the ledger so operations can commit together. */
class SetupStore(private val database: HubHelperDatabase) {
    val data = database.businessStateDao().observe("setup").map { it?.payload?.let { text -> decode(JSONObject(text)) } }

    suspend fun initialize(legacy: SetupPreferences) = database.withTransaction {
        if (database.businessStateDao().get("setup") == null && legacy.isComplete) {
            // Preserve legacy strings exactly. Input validation applies to new edits, not silent migration rounding.
            save(legacy.load(), "legacy setup migration")
        }
        val shiftMinutes = if ((read()?.shiftPreset ?: legacy.load().shiftPreset) == "SECOND") 600 else 480
        database.openHelper.writableDatabase.execSQL("UPDATE booked_pto_days SET durationMinutes = CASE WHEN usageType = 'REGULAR_PTO' THEN ? ELSE 480 END WHERE durationMinutes = 0", arrayOf(shiftMinutes))
    }

    suspend fun read(): SetupData? = database.businessStateDao().get("setup")?.payload?.let { decode(JSONObject(it)) }

    suspend fun save(data: SetupData, reason: String = "setup correction") = database.withTransaction {
        val previous = read()
        if (data.shiftPreset != null && (previous?.shiftPreset != data.shiftPreset || previous?.shiftEffectiveDate != data.shiftEffectiveDate)) {
            app.hubhelper.domain.ShiftPreset.valueOf(data.shiftPreset)
            val boundary = data.shiftEffectiveDate.takeIf(String::isNotBlank)?.let(java.time.LocalDate::parse)?.toString() ?: "unknown"
            database.businessStateDao().put(BusinessStateEntity("schedule:$boundary", data.shiftPreset, System.currentTimeMillis()))
        }
        if (previous != null && previous.shiftPreset != data.shiftPreset) {
            database.businessStateDao().audit(AuditEntryEntity(kind = "schedule changed", payload = "${java.time.LocalDate.now()}: ${previous.shiftPreset} -> ${data.shiftPreset}; existing booking durations preserved"))
        }
        val payload = encode(data).toString()
        database.businessStateDao().put(BusinessStateEntity("setup", payload, System.currentTimeMillis()))
        database.businessStateDao().audit(AuditEntryEntity(kind = reason, payload = payload))
    }

    companion object {
        fun encode(data: SetupData): JSONObject = JSONObject().apply {
            put("ptoBalanceHours", data.ptoBalanceHours); put("sickBalanceHours", data.sickBalanceHours)
            put("currentAttendancePoints", data.currentAttendancePoints); put("attendanceOpeningRemainder", data.attendanceOpeningRemainder)
            put("shiftPreset", data.shiftPreset); put("hireDate", data.hireDate); put("balancesAsOfDate", data.balancesAsOfDate)
            put("callInsRemaining", data.callInsRemaining); put("callInsBalanceYear", data.callInsBalanceYear)
            put("birthdayMonth", data.birthdayMonth); put("floatingHolidayAllowance", data.floatingHolidayAllowance)
            put("paydayAnchor", data.paydayAnchor); put("attendanceAsOfDate", data.attendanceAsOfDate); put("shiftEffectiveDate", data.shiftEffectiveDate)
        }
        fun decode(json: JSONObject): SetupData = SetupData(
            ptoBalanceHours = json.optString("ptoBalanceHours"), sickBalanceHours = json.optString("sickBalanceHours"),
            currentAttendancePoints = json.optString("currentAttendancePoints"), attendanceOpeningRemainder = json.optString("attendanceOpeningRemainder"),
            shiftPreset = if (json.isNull("shiftPreset")) null else json.getString("shiftPreset"),
            hireDate = json.optString("hireDate"), balancesAsOfDate = json.optString("balancesAsOfDate", java.time.LocalDate.now().toString()),
            callInsRemaining = json.optString("callInsRemaining"), callInsBalanceYear = json.optString("callInsBalanceYear"),
            birthdayMonth = json.optString("birthdayMonth"), floatingHolidayAllowance = json.optString("floatingHolidayAllowance"),
            paydayAnchor = json.optString("paydayAnchor"), attendanceAsOfDate = json.optString("attendanceAsOfDate"), shiftEffectiveDate = json.optString("shiftEffectiveDate"),
        )
    }
}
