package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.TimeBalanceKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimeBalanceRepository internal constructor(private val dao: TimeBalanceDao) {
    val adjustments: Flow<List<TimeBalanceAdjustment>> = dao.observeAll().map { rows ->
        rows.map { row ->
            TimeBalanceAdjustment(
                id = row.id.toString(),
                occurredOn = LocalDate.ofEpochDay(row.occurredEpochDay),
                kind = TimeBalanceKind.valueOf(row.kind),
                minutes = row.minutes,
                note = row.note,
            )
        }
    }

    suspend fun add(date: LocalDate, kind: TimeBalanceKind, minutes: Int, note: String?) {
        dao.insert(
            TimeBalanceAdjustmentEntity(
                occurredEpochDay = date.toEpochDay(),
                kind = kind.name,
                minutes = minutes,
                note = note?.trim()?.takeIf(String::isNotEmpty),
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
            TimeBalanceRepository(HubHelperDatabase.get(context).timeBalanceDao())
    }
}

