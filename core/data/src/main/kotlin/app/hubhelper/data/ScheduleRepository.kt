package app.hubhelper.data

import app.hubhelper.domain.ShiftPreset
import java.time.LocalDate

/** Effective dates are explicit; legacy schedules never acquire invented start dates. */
class ScheduleRepository(private val database: HubHelperDatabase) {
    data class Selection(val preset: ShiftPreset, val effectiveOn: LocalDate?, val assumed: Boolean) {
        val dayMinutes: Int get() = if (preset == ShiftPreset.SECOND) 600 else 480
    }
    suspend fun at(date: LocalDate): Selection? {
        val rows = database.businessStateDao().schedules()
        val dated = rows.mapNotNull { row ->
            val effective = runCatching { LocalDate.parse(row.key.removePrefix("schedule:")) }.getOrNull() ?: return@mapNotNull null
            if (effective > date) null else Selection(ShiftPreset.valueOf(row.payload), effective, false)
        }.maxByOrNull { it.effectiveOn!! }
        if (dated != null) return dated
        return rows.firstOrNull { it.key == "schedule:unknown" }?.let { Selection(ShiftPreset.valueOf(it.payload), null, true) }
    }
}
