package app.hubhelper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hubhelper.data.*
import app.hubhelper.domain.*
import kotlinx.coroutines.flow.*

class AppContainer(application: Application) {
    val attendance = AttendanceRepository.create(application)
    val time = TimeBalanceRepository.create(application)
    val documents = DocumentRepository.create(application)
    val notes = WorkNoteRepository.create(application)
    val holidays = HolidayRepository.create(application)
    val callIns = CallInRepository.create(application)
    val bookings = BookedPtoRepository.create(application)
}

data class LedgerState(
    val ready: Boolean = false,
    val events: List<AttendanceEvent> = emptyList(),
    val adjustments: List<TimeBalanceAdjustment> = emptyList(),
    val documents: List<WorkDocument> = emptyList(),
    val notes: List<WorkNote> = emptyList(),
    val holidays: List<PlantHoliday> = emptyList(),
    val callIns: List<CallInEvent> = emptyList(),
    val bookings: List<BookedPtoDay> = emptyList(),
    val error: String? = null,
)

/** One loaded ledger snapshot feeds Home, Calendar and Attendance consistently. */
class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    val container = AppContainer(application)
    private val records = combine(container.attendance.events, container.time.adjustments, container.documents.documents, container.notes.notes) { events, time, docs, notes ->
        LedgerState(events = events, adjustments = time, documents = docs, notes = notes)
    }
    private val calendar = combine(container.holidays.holidays, container.callIns.events, container.bookings.days) { holidays, callIns, bookings ->
        LedgerState(holidays = holidays, callIns = callIns, bookings = bookings)
    }
    val state = combine(records, calendar) { records, calendar ->
        records.copy(ready = true, holidays = calendar.holidays, callIns = calendar.callIns, bookings = calendar.bookings)
    }.catch { emit(LedgerState(error = it.message ?: "Unable to load records")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerState())
}
