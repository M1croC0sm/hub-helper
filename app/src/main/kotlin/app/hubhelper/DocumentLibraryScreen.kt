package app.hubhelper

import android.net.Uri
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.data.DocumentRepository
import app.hubhelper.domain.AttendancePrintoutParser
import app.hubhelper.domain.ParsedAttendanceStatement
import app.hubhelper.domain.WorkDocument
import app.hubhelper.domain.WorkNote
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.ExceptionFormParser
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.HolidayCalendarParser
import java.time.LocalDate

@Composable
fun DocumentLibraryScreen(
    padding: PaddingValues,
    documents: List<WorkDocument>,
    onImport: (List<Uri>, DocumentCategory) -> Unit,
    onDelete: (WorkDocument) -> Unit,
    onApplyAttendanceStatement: (WorkDocument, ParsedAttendanceStatement) -> Unit,
    appDate: LocalDate,
    notes: List<WorkNote>,
    bookedPtoDays: List<BookedPtoDay>,
    onBookPto: (LocalDate, String?) -> Unit,
    holidays: List<PlantHoliday>,
    onAddHoliday: (LocalDate, String) -> Unit,
    onAddNote: (LocalDate, String) -> Unit,
    onDeleteNote: (WorkNote) -> Unit,
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf<DocumentCategory?>(null) }
    var showAddDocument by remember { mutableStateOf(false) }
    var viewingDocument by remember { mutableStateOf<WorkDocument?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<WorkDocument?>(null) }
    var noteText by remember { mutableStateOf("") }
    var noteDeleteCandidate by remember { mutableStateOf<WorkNote?>(null) }
    var attendancePreview by remember { mutableStateOf<Pair<WorkDocument, ParsedAttendanceStatement>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val selectedCategory = category
        if (selectedCategory != null && uris.isNotEmpty()) onImport(uris, selectedCategory)
    }
    var cameraUri by remember { mutableStateOf(newCameraUri(context)) }
    var capturedPageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showCaptureMore by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val selectedCategory = category
        if (saved && selectedCategory != null) {
            capturedPageUris = capturedPageUris + cameraUri
            showCaptureMore = true
        }
    }
    val visible = documents

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { category = null; showAddDocument = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add document") }
        Text("Saved documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (visible.isEmpty()) Text(if (documents.isEmpty()) "No documents added yet." else "Nothing matched your search.")
        visible.forEach { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(document.title, fontWeight = FontWeight.SemiBold)
                    Text("${friendlyCategory(document.category)} • ${friendlyTextStatus(document)}")
                    OutlinedButton(onClick = { viewingDocument = document }, modifier = Modifier.fillMaxWidth()) { Text("VIEW DOCUMENT AND OCR") }
                    val recognizedText = document.ocrText
                    if (!recognizedText.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            expandedId = if (expandedId == document.id) null else document.id
                        }) { Text(if (expandedId == document.id) "Hide readable text" else "Read detected text") }
                        if (expandedId == document.id) Text(recognizedText)
                        if (document.category == DocumentCategory.ATTENDANCE) {
                            val parsed = remember(recognizedText) { AttendancePrintoutParser().parse(recognizedText) }
                            Text("${parsed.rows.size} dated attendance rows recognized. These become permanent calendar records.")
                            Text("Current points remain manually controlled in Settings.", fontWeight = FontWeight.SemiBold)
                            parsed.rows.filter { it.adjustmentHalfPoints != null && it.adjustmentHalfPoints != 0 }.forEach { row ->
                                Text(
                                    "${row.date.monthDayYear()}  •  change ${HalfPoints(row.adjustmentHalfPoints!!).asDisplayValue()}  •  total ${HalfPoints(row.runningTotalHalfPoints).asDisplayValue()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (parsed.rows.isNotEmpty()) {
                                Button(onClick = { attendancePreview = document to parsed }) {
                                    Text("Review and confirm attendance rows")
                                }
                            }
                            parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (document.category == DocumentCategory.HOLIDAY_CALENDAR) {
                            val parsed = remember(recognizedText, appDate.year) { HolidayCalendarParser().parse(recognizedText, appDate.year) }
                            SectionLabel("Detected holidays", color = HubThemeDesign.tokens.attention)
                            Text("Review each result before adding it to the calendar.")
                            val unsaved = parsed.holidays.filterNot { candidate ->
                                holidays.any { it.date == candidate.date && it.name.equals(candidate.name, ignoreCase = true) }
                            }
                            if (parsed.holidays.isEmpty()) Text("No holiday rows were recognized. Try a clearer, closer photo of each page.")
                            if (unsaved.size > 1) {
                                Button(
                                    onClick = { unsaved.forEach { onAddHoliday(it.date, it.name) } },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("ADD ALL ${unsaved.size} REVIEWED HOLIDAYS") }
                            }
                            parsed.holidays.forEach { candidate ->
                                val saved = holidays.any { it.date == candidate.date && it.name.equals(candidate.name, ignoreCase = true) }
                                OutlinedButton(
                                    onClick = { onAddHoliday(candidate.date, candidate.name) },
                                    enabled = !saved,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text("${candidate.date.monthDayYear()} • ${candidate.name}")
                                        Text(if (saved) "ADDED" else "ADD TO PLANT CALENDAR", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (document.category == DocumentCategory.EXCEPTION_FORM) {
                            val parsed = remember(recognizedText, appDate) { ExceptionFormParser().parse(recognizedText, appDate) }
                            SectionLabel("Detected booked dates", color = HubThemeDesign.tokens.pto)
                            if (parsed.bookedDates.isEmpty()) Text("No dates recognized. Review the detected text or add the date manually from Log.")
                            parsed.bookedDates.forEach { date ->
                                val saved = bookedPtoDays.any { it.date == date && it.sourceDocumentId == document.id }
                                OutlinedButton(
                                    onClick = { onBookPto(date, document.id) },
                                    enabled = !saved,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (saved) "${date.monthDayYear()} • SAVED" else "SAVE ${date.monthDayYear()} AS BOOKED PTO") }
                            }
                            parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    OutlinedButton(onClick = { deleteCandidate = document }) { Text("Delete document") }
                }
            }
        }
        Text("Work notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Write a note for $appDate") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAddNote(appDate, noteText); noteText = "" },
            enabled = noteText.isNotBlank(),
        ) { Text("Save note") }
        notes.forEach { note ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(note.date.toString(), style = MaterialTheme.typography.labelLarge)
                    Text(note.text)
                    OutlinedButton(onClick = { noteDeleteCandidate = note }) { Text("Delete note") }
                }
            }
        }
    }

    if (showAddDocument) {
        AlertDialog(
            onDismissRequest = { showAddDocument = false },
            title = { Text(if (category == null) "What kind of document is this?" else "How would you like to add it?") },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (category == null) {
                        documentCategoryChoices.forEach { option ->
                            OutlinedButton(onClick = { category = option }, modifier = Modifier.fillMaxWidth()) {
                                Text(friendlyCategory(option))
                            }
                        }
                    } else {
                        Text(friendlyCategory(category!!), fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = {
                                showAddDocument = false
                                capturedPageUris = emptyList()
                                cameraUri = newCameraUri(context)
                                cameraLauncher.launch(cameraUri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (category == DocumentCategory.ATTENDANCE) "Scan attendance pages" else "Scan one or more pages") }
                        OutlinedButton(
                            onClick = {
                                showAddDocument = false
                                picker.launch(arrayOf("image/*", "application/pdf"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Choose one or more files") }
                        TextButton(onClick = { category = null }) { Text("Choose a different type") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddDocument = false }) { Text("Cancel") } },
        )
    }

    viewingDocument?.let { document ->
        DocumentViewerDialog(document = document, onDismiss = { viewingDocument = null })
    }

    if (showCaptureMore) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Page ${capturedPageUris.size} captured") },
            text = { Text("Add the next page, or finish to save all ${capturedPageUris.size} page${if (capturedPageUris.size == 1) "" else "s"} as one document.") },
            confirmButton = {
                Button(onClick = {
                    showCaptureMore = false
                    cameraUri = newCameraUri(context)
                    cameraLauncher.launch(cameraUri)
                }) { Text("Scan another page") }
            },
            dismissButton = {
                TextButton(onClick = {
                    category?.let { onImport(capturedPageUris, it) }
                    capturedPageUris = emptyList()
                    showCaptureMore = false
                }) { Text("Finish one document") }
            },
        )
    }

    deleteCandidate?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete original document?") },
            text = { Text("This permanently removes the saved original and any detected text. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(document)
                    deleteCandidate = null
                }) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }
    noteDeleteCandidate?.let { note ->
        AlertDialog(
            onDismissRequest = { noteDeleteCandidate = null },
            title = { Text("Delete note?") },
            text = { Text("This permanently removes the note.") },
            confirmButton = {
                TextButton(onClick = { onDeleteNote(note); noteDeleteCandidate = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { noteDeleteCandidate = null }) { Text("Cancel") } },
        )
    }
    attendancePreview?.let { (document, parsed) ->
        AttendanceImportPreviewDialog(
            parsed = parsed,
            manualTotal = null,
            onDismiss = { attendancePreview = null },
            onConfirm = {
                onApplyAttendanceStatement(document, parsed)
                attendancePreview = null
            },
        )
    }
}

@Composable
fun AttendanceImportPreviewDialog(
    parsed: ParsedAttendanceStatement,
    manualTotal: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val rows = parsed.rows.filter { it.adjustmentHalfPoints != null && it.adjustmentHalfPoints != 0 }
    val detectedTotal = parsed.currentTotalHalfPoints?.let(::HalfPoints)?.asDisplayValue()
    val totalMismatch = manualTotal?.toBigDecimalOrNull()?.let { entered ->
        detectedTotal?.toBigDecimalOrNull()?.compareTo(entered) != 0
    } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check attendance dates") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("We found ${rows.size} dated row${if (rows.size == 1) "" else "s"}. Only the rows below will be saved.")
                if (detectedTotal != null) Text("Detected sheet total: $detectedTotal", fontWeight = FontWeight.SemiBold)
                if (manualTotal != null && totalMismatch) {
                    Text("Your entered total is $manualTotal. The sheet total does not match, so your entered total will be kept.", color = MaterialTheme.colorScheme.error)
                }
                if (rows.isEmpty()) Text("No complete point rows were found. Nothing will be saved.")
                rows.forEach { row ->
                    val change = HalfPoints(row.adjustmentHalfPoints!!).asDisplayValue()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(row.date.monthDayYear(), fontWeight = FontWeight.SemiBold)
                            Text("Point change: $change   •   Running total: ${HalfPoints(row.runningTotalHalfPoints).asDisplayValue()}")
                            if (row.comment.isNotBlank()) Text(row.comment, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = rows.isNotEmpty()) { Text("Confirm and save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DocumentViewerDialog(document: WorkDocument, onDismiss: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, document) {
        value = withContext(Dispatchers.IO) { loadDocumentPreview(document) }
    }
    var scale by remember(document) { mutableFloatStateOf(1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title) },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(friendlyCategory(document.category), style = MaterialTheme.typography.labelLarge)
                if (bitmap != null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 430.dp).clip(MaterialTheme.shapes.medium)) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Original document page. Pinch or drag to zoom.",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale).pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f) }
                            },
                        )
                    }
                    Text("Pinch or drag to zoom • zoom ${"%.1f".format(scale)}×", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("This document preview is not available, but the original file is saved securely on this device.")
                }
                HorizontalDivider()
                Text("Detected text", fontWeight = FontWeight.SemiBold)
                Text(document.ocrText?.takeIf(String::isNotBlank) ?: "OCR text is not available yet.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun loadDocumentPreview(document: WorkDocument): Bitmap? = runCatching {
    val file = File(document.privatePath)
    when {
        document.mimeType == DocumentRepository.MULTI_PAGE_MIME -> ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) return@use BitmapFactory.decodeStream(zip)
                entry = zip.nextEntry
            }
            null
        }
        document.mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true) -> {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                if (renderer.pageCount == 0) null else renderer.openPage(0).use { page ->
                    Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also { bitmap ->
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
        else -> BitmapFactory.decodeFile(file.absolutePath)
    }
}.getOrNull()

private fun newCameraUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera-captures/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun friendlyCategory(category: DocumentCategory): String = when (category) {
    DocumentCategory.ATTENDANCE -> "Attendance or points sheet"
    DocumentCategory.HOLIDAY_CALENDAR -> "Holiday calendar"
    DocumentCategory.EXCEPTION_FORM -> "PTO exception form"
    DocumentCategory.PTO -> "PTO or vacation"
    DocumentCategory.PAY -> "Pay stub"
    DocumentCategory.BENEFITS -> "Benefits"
    DocumentCategory.POLICY -> "Work policy"
    DocumentCategory.CONTRACT -> "Union contract"
    DocumentCategory.OTHER -> "Something else"
}

private val documentCategoryChoices = listOf(
    DocumentCategory.ATTENDANCE,
    DocumentCategory.HOLIDAY_CALENDAR,
    DocumentCategory.EXCEPTION_FORM,
    DocumentCategory.PAY,
    DocumentCategory.OTHER,
)

private fun friendlyTextStatus(document: WorkDocument): String = when (document.ocrStatus.name) {
    "COMPLETE" -> "text ready"
    "PROCESSING" -> "reading text"
    "FAILED" -> "could not read text"
    "UNSUPPORTED" -> "saved original"
    else -> "waiting to read text"
}
