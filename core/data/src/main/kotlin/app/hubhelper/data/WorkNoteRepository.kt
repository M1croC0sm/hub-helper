package app.hubhelper.data

import android.content.Context
import app.hubhelper.domain.WorkNote
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkNoteRepository internal constructor(private val dao: WorkNoteDao) {
    val notes: Flow<List<WorkNote>> = dao.observeAll().map { rows ->
        rows.map { WorkNote(it.id.toString(), LocalDate.ofEpochDay(it.dateEpochDay), it.text) }
    }

    suspend fun add(date: LocalDate, text: String) {
        require(text.isNotBlank())
        dao.insert(WorkNoteEntity(dateEpochDay = date.toEpochDay(), text = text.trim(), createdAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun delete(note: WorkNote) {
        val id = note.id.toLongOrNull() ?: return
        dao.delete(WorkNoteEntity(id, note.date.toEpochDay(), note.text, 0))
    }

    companion object {
        fun create(context: Context) = WorkNoteRepository(HubHelperDatabase.get(context).workNoteDao())
    }
}

