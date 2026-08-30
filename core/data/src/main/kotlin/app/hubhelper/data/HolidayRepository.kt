package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.HolidayCalendarParser
import app.hubhelper.domain.ContractHolidayCalculator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HolidayRepository internal constructor(private val dao: HolidayDao) {
    private val holidayNames = HolidayCalendarParser()
    val holidays: Flow<List<PlantHoliday>> = dao.observeAll().map { rows ->
        rows.map {
            val date = LocalDate.ofEpochDay(it.dateEpochDay)
            PlantHoliday(it.id.toString(), date, holidayNames.resolvedName(date, it.name))
        }.filterNot { holidayNames.isFloatingHolidayName(it.name) }
    }

    suspend fun add(date: LocalDate, name: String) {
        require(name.isNotBlank())
        val normalized = holidayNames.resolvedName(date, name)
        if (holidayNames.isFloatingHolidayName(normalized)) return
        if (dao.count(date.toEpochDay(), normalized) == 0) {
            dao.insert(HolidayEntity(dateEpochDay = date.toEpochDay(), name = normalized))
        }
    }

    suspend fun delete(holiday: PlantHoliday) {
        val id = holiday.id.toLongOrNull() ?: return
        dao.delete(HolidayEntity(id, holiday.date.toEpochDay(), holiday.name))
    }

    suspend fun ensureContractHolidays(year: Int, secondShift: Boolean) {
        ContractHolidayCalculator.forYear(year, secondShift).forEach { add(it.date, it.name) }
    }

    companion object {
        fun create(context: Context) = HolidayRepository(HubHelperDatabase.get(context).holidayDao())
    }
}
