package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.TimeBalanceKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimeBalanceRepository internal constructor(private val dao: TimeBalanceDao, private val database: HubHelperDatabase) {
    val adjustments: Flow<List<TimeBalanceAdjustment>> = dao.observeAll().map { rows ->
        rows.map { row ->
            TimeBalanceAdjustment(
                id = row.id.toString(),
                occurredOn = LocalDate.ofEpochDay(row.occurredEpochDay),
                kind = TimeBalanceKind.valueOf(row.kind),
                minutes = row.minutes,
                note = row.note,
                bookingId = row.bookingId,
            )
        }
    }

    suspend fun add(date: LocalDate, kind: TimeBalanceKind, minutes: Int, note: String?, bookingId: String? = null) = database.withTransaction {
        if (bookingId != null) {
            val booking = requireNotNull(database.bookedPtoDao().byStableId(bookingId)) { "Booking no longer exists" }
            require(kind == TimeBalanceKind.PTO && minutes < 0 && booking.dateEpochDay == date.toEpochDay() && booking.usageType == "REGULAR_PTO" && booking.bookingStatus != "CANCELLED") { "Usage does not match booking" }
            require(dao.getAll().none { it.bookingId == bookingId } && database.callInDao().getAll().none { it.bookingId == bookingId }) { "This booking already has recorded usage" }
            database.bookedPtoDao().setStatus(booking.id, "TAKEN")
        }
        dao.insert(
            TimeBalanceAdjustmentEntity(
                occurredEpochDay = date.toEpochDay(),
                kind = kind.name,
                minutes = minutes,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                bookingId = bookingId,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(adjustment: TimeBalanceAdjustment) {
        val id = adjustment.id.toLongOrNull() ?: return
        dao.delete(
            TimeBalanceAdjustmentEntity(
                id = id,
                occurredEpochDay = adjustment.occurredOn.toEpochDay(),
                kind = adjustment.kind.name,
                minutes = adjustment.minutes,
                note = adjustment.note,
                createdAtEpochMillis = 0,
            ),
        )
    }

    companion object {
        fun create(context: Context): TimeBalanceRepository =
            TimeBalanceRepository(HubHelperDatabase.get(context).timeBalanceDao(), HubHelperDatabase.get(context))
    }
}

