package app.hubhelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val mimeType: String,
    val originalName: String,
    val privatePath: String,
    val importedAtEpochMillis: Long,
    val sha256: String,
    val ocrText: String?,
    val ocrStatus: String,
)

