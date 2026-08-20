package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.BookedTimeType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookedPtoRepository internal constructor(private val dao: BookedPtoDao) {
    val days: Flow<List<BookedPtoDay>> = dao.observeAll().map { rows ->
        rows.map { BookedPtoDay(it.id.toString(), LocalDate.ofEpochDay(it.dateEpochDay), it.sourceDocumentId, BookedTimeType.valueOf(it.usageType)) }
    }

    suspend fun add(date: LocalDate, sourceDocumentId: String? = null, type: BookedTimeType = BookedTimeType.REGULAR_PTO) {
        if (dao.count(date.toEpochDay(), sourceDocumentId) == 0) {
            dao.insert(BookedPtoEntity(dateEpochDay = date.toEpochDay(), sourceDocumentId = sourceDocumentId, usageType = type.name, createdAtEpochMillis = System.currentTimeMillis()))
        }
    }

    suspend fun delete(day: BookedPtoDay) {
        val id = day.id.toLongOrNull() ?: return
        dao.delete(BookedPtoEntity(id, day.date.toEpochDay(), day.sourceDocumentId, day.type.name, 0))
    }

    companion object {
        fun create(context: Context) = BookedPtoRepository(HubHelperDatabase.get(context).bookedPtoDao())
    }
}
