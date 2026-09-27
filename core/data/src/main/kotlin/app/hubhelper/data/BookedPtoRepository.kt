package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import app.hubhelper.domain.BookingStatus
import org.json.JSONObject
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.BookedTimeType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookedPtoRepository internal constructor(private val dao: BookedPtoDao, private val database: HubHelperDatabase) {
    val days: Flow<List<BookedPtoDay>> = dao.observeAll().map { rows ->
        rows.map { BookedPtoDay(it.id.toString(), LocalDate.ofEpochDay(it.dateEpochDay), it.sourceDocumentId, BookedTimeType.valueOf(it.usageType), it.durationMinutes, BookingStatus.valueOf(it.bookingStatus), it.stableId, it.legacyAssumption) }
    }

    suspend fun add(date: LocalDate, sourceDocumentId: String? = null, type: BookedTimeType = BookedTimeType.REGULAR_PTO,
        durationMinutes: Int? = null, status: BookingStatus = BookingStatus.APPROVED) = database.withTransaction {
        val setup = database.businessStateDao().get("setup")?.let { JSONObject(it.payload) }
        val schedule = ScheduleRepository(database).at(date)
        val minutes = durationMinutes ?: if (type == BookedTimeType.REGULAR_PTO && schedule != null) schedule.dayMinutes else if (type == BookedTimeType.REGULAR_PTO && setup?.optString("shiftPreset") == "SECOND") 600 else 480
        require(minutes in 1..1440) { "Enter a duration from 1 to 1440 minutes" }
        require(dao.getAll().none { it.dateEpochDay == date.toEpochDay() && it.usageType == type.name && it.bookingStatus != "CANCELLED" }) { "This date and time type are already booked" }
        if (type != BookedTimeType.REGULAR_PTO) {
            val allowance = setup?.optString("floatingHolidayAllowance")?.toIntOrNull() ?: 0
            val bookedTypes = dao.getAll().filter { LocalDate.ofEpochDay(it.dateEpochDay).year == date.year && it.usageType != "REGULAR_PTO" && it.bookingStatus != "CANCELLED" }.map { it.usageType }.toSet()
            val used = database.timeBalanceDao().getAll().filter { LocalDate.ofEpochDay(it.occurredEpochDay).year == date.year && it.minutes < 0 && it.kind.startsWith("FLOATING_") }
            require(bookedTypes.size + used.map { it.kind }.distinct().size < allowance) { "No floating holidays remain" }
            require(type.name !in bookedTypes && used.none { (type == BookedTimeType.BIRTHDAY_FLOATING && it.kind == "FLOATING_BIRTHDAY") || (type == BookedTimeType.ANYTIME_FLOATING && it.kind == "FLOATING_ANYTIME") }) { "This floating holiday is already used" }
            require(type != BookedTimeType.BIRTHDAY_FLOATING || setup?.optString("birthdayMonth")?.toIntOrNull() == date.monthValue) { "Birthday floating must be used in your birthday month" }
        }
        dao.insert(BookedPtoEntity(dateEpochDay = date.toEpochDay(), sourceDocumentId = sourceDocumentId,
            usageType = type.name, createdAtEpochMillis = System.currentTimeMillis(), durationMinutes = minutes,
            bookingStatus = status.name, legacyAssumption = durationMinutes == null && type == BookedTimeType.REGULAR_PTO && (schedule == null || schedule.assumed)))
    }

    suspend fun delete(day: BookedPtoDay) {
        val id = day.id.toLongOrNull() ?: return
        dao.setStatus(id, BookingStatus.CANCELLED.name)
    }

    suspend fun setStatus(day: BookedPtoDay, status: BookingStatus) = dao.setStatus(day.id.toLong(), status.name)

    companion object {
        fun create(context: Context) = BookedPtoRepository(HubHelperDatabase.get(context).bookedPtoDao(), HubHelperDatabase.get(context))
    }
}
