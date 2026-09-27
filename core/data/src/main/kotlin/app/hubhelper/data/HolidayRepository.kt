package app.hubhelper.data

import android.content.Context
import androidx.room.withTransaction
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.HolidayCalendarParser
import app.hubhelper.domain.ContractHolidayCalculator
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HolidayRepository internal constructor(private val dao: HolidayDao, private val database: HubHelperDatabase) {
    private val holidayNames = HolidayCalendarParser()
    val holidays: Flow<List<PlantHoliday>> = dao.observeAll().map { rows ->
        rows.map {
            val date = LocalDate.ofEpochDay(it.dateEpochDay)
            PlantHoliday(it.id.toString(), date, holidayNames.resolvedName(date, it.name))
        }.filterNot { holidayNames.isFloatingHolidayName(it.name) }
    }

    suspend fun add(date: LocalDate, name: String) = database.withTransaction {
        require(name.isNotBlank())
        val normalized = holidayNames.resolvedName(date, name)
        if (holidayNames.isFloatingHolidayName(normalized)) return@withTransaction
        val generated = dao.getAll().filter { it.origin == "CONTRACT" && it.name.equals(normalized, true) && LocalDate.ofEpochDay(it.dateEpochDay).year == date.year }
        generated.forEach { dao.update(it.copy(suppressed = true)) }
        val same = dao.getAll().firstOrNull { it.origin == "USER" && it.dateEpochDay == date.toEpochDay() && it.name.equals(normalized, true) }
        if (same != null) dao.update(same.copy(suppressed = false))
        else {
            dao.insert(HolidayEntity(dateEpochDay = date.toEpochDay(), name = normalized))
        }
    }

    suspend fun delete(holiday: PlantHoliday) {
        val id = holiday.id.toLongOrNull() ?: return
        val existing = dao.getAll().firstOrNull { it.id == id } ?: return
        dao.update(existing.copy(suppressed = true))
    }

    suspend fun ensureContractHolidays(year: Int, secondShift: Boolean) = database.withTransaction {
        val existing = dao.getAll()
        ContractHolidayCalculator.forYear(year, secondShift).forEach { holiday ->
            val identity = "contract:$year:${holiday.name}"
            val saved = existing.firstOrNull { it.stableId == identity }
            if (saved == null) {
                // Existing imported or legacy dates remain authoritative until reviewed.
                val legacy = existing.firstOrNull { LocalDate.ofEpochDay(it.dateEpochDay).year == year && it.name == holiday.name }
                if (legacy == null) dao.insert(HolidayEntity(dateEpochDay = holiday.date.toEpochDay(), name = holiday.name, stableId = identity, origin = "CONTRACT"))
            } else if (!saved.suppressed && LocalDate.ofEpochDay(saved.dateEpochDay) >= LocalDate.now()) {
                dao.update(saved.copy(dateEpochDay = holiday.date.toEpochDay()))
            }
        }
    }

    companion object {
        fun create(context: Context) = HolidayRepository(HubHelperDatabase.get(context).holidayDao(), HubHelperDatabase.get(context))
    }
}
