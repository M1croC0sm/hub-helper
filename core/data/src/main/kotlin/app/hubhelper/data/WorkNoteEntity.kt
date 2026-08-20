package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_notes")
data class WorkNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val text: String,
    val createdAtEpochMillis: Long,
)

