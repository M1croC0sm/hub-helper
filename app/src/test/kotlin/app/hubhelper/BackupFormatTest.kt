package app.hubhelper

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    @Test fun `current exporter version is accepted`() {
        BackupFormat.validate(JSONObject().put("formatVersion", BackupFormat.CURRENT_VERSION))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `undocumented version is rejected`() { BackupFormat.validate(JSONObject().put("formatVersion", 5)) }
    @Test(expected = IllegalArgumentException::class)
    fun `duplicate originals are rejected before any restore`() {
        val doc = JSONObject().put("id", "same").put("category", "OTHER").put("archivePath", "documents/same")
        BackupFormat.validate(JSONObject().put("formatVersion", 6).put("documents", JSONArray().put(doc).put(doc)))
    }
    @Test fun `setup round trip preserves payday and balances`() {
        val original = SetupData(ptoBalanceHours = "40.25", sickBalanceHours = "8", paydayAnchor = "2026-09-04", shiftPreset = "SECOND")
        val json = SetupStore.encode(original)
        BackupFormat.validate(JSONObject().put("formatVersion", 6).put("setup", json))
        assertEquals(original, SetupStore.decode(json))
    }
    @Test(expected = ArithmeticException::class)
    fun `fractional minute setup is rejected`() {
        BackupFormat.validate(JSONObject().put("formatVersion", 6).put("setup", JSONObject().put("ptoBalanceHours", "0.001")))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `negative points in an event are rejected`() {
        BackupFormat.validate(JSONObject().put("formatVersion", 6).put("attendanceEvents", JSONArray().put(JSONObject()
            .put("date", "2026-09-01").put("type", "TARDY").put("status", "CONFIRMED").put("halfPoints", -1))))
    }
}
