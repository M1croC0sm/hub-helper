package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.CallInEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallInRepository internal constructor(private val dao: CallInDao) {
    val events: Flow<List<CallInEvent>> = dao.observeAll().map { rows ->
        rows.map { CallInEvent(it.id.toString(), LocalDate.ofEpochDay(it.occurredEpochDay), it.ptoMinutes) }
    }

    suspend fun add(date: LocalDate, ptoMinutes: Int) {
        require(ptoMinutes > 0)
        dao.insert(CallInEntity(occurredEpochDay = date.toEpochDay(), ptoMinutes = ptoMinutes, createdAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun delete(event: CallInEvent) {
        val id = event.id.toLongOrNull() ?: return
        dao.delete(CallInEntity(id, event.occurredOn.toEpochDay(), event.ptoMinutes, 0))
    }

    companion object {
        fun create(context: Context) = CallInRepository(HubHelperDatabase.get(context).callInDao())
    }
}
