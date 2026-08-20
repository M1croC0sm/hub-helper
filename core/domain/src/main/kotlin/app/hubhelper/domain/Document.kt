package app.hubhelper.domain

enum class DocumentCategory { ATTENDANCE, HOLIDAY_CALENDAR, EXCEPTION_FORM, PTO, PAY, BENEFITS, POLICY, CONTRACT, OTHER }

enum class OcrStatus { NOT_STARTED, PROCESSING, COMPLETE, FAILED, UNSUPPORTED }

data class WorkDocument(
    val id: String,
    val title: String,
    val category: DocumentCategory,
    val mimeType: String,
    val originalName: String,
    val privatePath: String,
    val importedAtEpochMillis: Long,
    val sha256: String,
    val ocrText: String?,
    val ocrStatus: OcrStatus,
)
