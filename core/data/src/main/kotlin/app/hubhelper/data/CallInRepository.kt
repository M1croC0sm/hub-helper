package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONObject
import app.hubhelper.domain.CallInEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallInRepository internal constructor(private val dao: CallInDao, private val database: HubHelperDatabase) {
    val events: Flow<List<CallInEvent>> = dao.observeAll().map { rows ->
        rows.map { CallInEvent(it.id.toString(), LocalDate.ofEpochDay(it.occurredEpochDay), it.ptoMinutes, it.bookingId) }
    }

    suspend fun add(date: LocalDate, ptoMinutes: Int, bookingId: String? = null) = database.withTransaction {
        require(ptoMinutes in 1..1440)
        if (bookingId != null) {
            val booking = requireNotNull(database.bookedPtoDao().byStableId(bookingId)) { "Booking missing" }
            require(booking.dateEpochDay == date.toEpochDay() && booking.usageType == "REGULAR_PTO" && booking.bookingStatus != "CANCELLED") { "Call-in does not match booking" }
            require(dao.getAll().none { it.bookingId == bookingId } && database.timeBalanceDao().getAll().none { it.bookingId == bookingId }) { "Booking already has actual usage" }
            database.bookedPtoDao().setStatus(booking.id, "TAKEN")
        }
        adjustAllowance(date.year, -1)
        dao.insert(CallInEntity(occurredEpochDay = date.toEpochDay(), ptoMinutes = ptoMinutes, bookingId = bookingId, createdAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun delete(event: CallInEvent) = database.withTransaction {
        val id = event.id.toLongOrNull() ?: return@withTransaction
        val stored = dao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
        adjustAllowance(LocalDate.ofEpochDay(stored.occurredEpochDay).year, 1)
        dao.delete(stored)
    }

    private suspend fun adjustAllowance(year: Int, delta: Int) {
        val state = database.businessStateDao().get("setup") ?: return
        val json = JSONObject(state.payload)
        val remaining = if (json.optString("callInsBalanceYear").toIntOrNull() == year) {
            json.optString("callInsRemaining").toIntOrNull()
        } else null
        val current = remaining ?: (5 - dao.getAll().count { LocalDate.ofEpochDay(it.occurredEpochDay).year == year })
        require(delta > 0 || current > 0) { "No call-ins remain for $year" }
        json.put("callInsBalanceYear", year.toString()).put("callInsRemaining", (current + delta).coerceIn(0, 5).toString())
        database.businessStateDao().put(state.copy(payload = json.toString(), updatedAt = System.currentTimeMillis()))
    }
    companion object {
        fun create(context: Context) = CallInRepository(HubHelperDatabase.get(context).callInDao(), HubHelperDatabase.get(context))
    }
}
