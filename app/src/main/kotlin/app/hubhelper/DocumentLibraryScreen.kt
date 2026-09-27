package app.hubhelper

import android.net.Uri
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
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
import java.io.ByteArrayInputStream
import kotlinx.coroutines.launch
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
    val activity = requireNotNull(androidx.activity.compose.LocalActivity.current) as androidx.fragment.app.FragmentActivity
    val model = androidx.lifecycle.ViewModelProvider(activity)[DocumentsViewModel::class.java]
    val repository = remember { DocumentRepository.create(context) }
    val ocr = remember { DocumentOcr(context, repository) }
    val scope = androidx.lifecycle.ViewModelProvider(activity)[AppOperations::class.java]
    var filter by rememberSaveable { mutableStateOf<DocumentCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var requestedPage by remember { mutableStateOf(0) }
    LaunchedEffect(query, documents) { model.search(query) }
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
    val matchingIds = model.results.map { it.documentId }.toSet()
    val visible = documents.filter { (filter == null || it.category == filter) && (query.isBlank() || it.title.contains(query, true) || it.id in matchingIds) }

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
        OutlinedTextField(query, { query = it }, label = { Text("Search titles and page text") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            androidx.compose.material3.FilterChip(filter == null, { filter = null }, label = { Text("All categories") })
            DocumentCategory.entries.forEach { option -> androidx.compose.material3.FilterChip(filter == option, { filter = option }, label = { Text(friendlyCategory(option)) }) }
        }
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        model.results.forEach { result ->
            documents.firstOrNull { it.id == result.documentId && !it.originalDeleted }?.let { document ->
                TextButton(onClick = { requestedPage = result.pageNumber - 1; viewingDocument = document }) {
                    Text("${document.title} • Page ${result.pageNumber}: ${result.text.take(160)}")
                }
            }
        }
        Text("Saved documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (visible.isEmpty()) Text(if (documents.isEmpty()) "No documents added yet." else "Nothing matched your search.")
        visible.forEach { document ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(document.title, fontWeight = FontWeight.SemiBold)
                    Text("${friendlyCategory(document.category)} • ${friendlyTextStatus(document)}")
                    OutlinedButton(onClick = { requestedPage = 0; viewingDocument = document }, modifier = Modifier.fillMaxWidth()) { Text("VIEW DOCUMENT AND OCR") }
                    if (document.originalDeleted) Text("Original deleted. Linked records keep this source identity.")
                    else Row {
                        TextButton(onClick = { ocr.enqueue(document) }) { Text("Read / retry OCR") }
                        TextButton(onClick = { ocr.cancel(document) }) { Text("Cancel OCR") }
                    }
                    var title by rememberSaveable(document.id) { mutableStateOf(document.title) }
                    OutlinedTextField(title, { title = it }, label = { Text("Document title") })
                    TextButton(onClick = { scope.launch { repository.rename(document.id, title) } }, enabled = title.isNotBlank() && title != document.title) { Text("Save title") }
                    val recognizedText = document.ocrText
                    if (!recognizedText.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            expandedId = if (expandedId == document.id) null else document.id
                        }) { Text(if (expandedId == document.id) "Hide readable text" else "Read detected text") }
                        if (expandedId == document.id) Text(recognizedText)
                        if (document.category == DocumentCategory.ATTENDANCE) {
                            val parsed = remember(recognizedText) { AttendancePrintoutParser().parse(recognizedText) }
                            Text("${parsed.rows.size} dated attendance rows recognized. These become permanent calendar records.")
                            Text("Review the statement date, balance, and detected rows before reconciliation.", fontWeight = FontWeight.SemiBold)
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
        var noteQuery by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(noteQuery, { noteQuery = it }, label = { Text("Search work notes") })
        notes.filter { noteQuery.isBlank() || it.text.contains(noteQuery, true) || it.date.toString().contains(noteQuery) }.forEach { note ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(note.date.toString(), style = MaterialTheme.typography.labelLarge)
                    var draft by rememberSaveable(note.id) { mutableStateOf(note.text) }
                    OutlinedTextField(draft, { draft = it }, label = { Text("Note") })
                    TextButton(onClick = { scope.launch { app.hubhelper.data.WorkNoteRepository.create(context).update(note, draft) } }, enabled = draft.isNotBlank() && draft != note.text) { Text("Save note changes") }
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
        DocumentViewerDialog(document = document, initialPage = requestedPage, onDismiss = { viewingDocument = null })
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
        val attendanceLinks by produceState(0, document.id) {
            value = app.hubhelper.data.HubHelperDatabase.get(context).attendanceDao().getAll().count { it.sourceDocumentId == document.id }
        }
        val bookingLinks = bookedPtoDays.count { it.sourceDocumentId == document.id }
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete original document?") },
            text = { Text("This source supports $attendanceLinks attendance records and $bookingLinks bookings. This permanently removes the original and detected text. Attendance and booked-time records keep the source identity and will show that the original is unavailable. This cannot be undone.") },
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
            calculationDate = appDate,
            sourceDocumentId = document.id,
            manualTotal = null,
            onDismiss = { attendancePreview = null },
            onConfirm = { reviewed ->
                onApplyAttendanceStatement(document, reviewed)
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
    onConfirm: (ParsedAttendanceStatement) -> Unit,
    sourceDocumentId: String? = null,
    calculationDate: LocalDate = LocalDate.now(),
) {
    val rows = parsed.rows.filter { it.adjustmentHalfPoints != null && it.adjustmentHalfPoints != 0 }
    var accepted by remember(parsed) { mutableStateOf(rows.map { true }) }
    var edits by remember(parsed) { mutableStateOf(rows) }
    var statementDate by remember(parsed) { mutableStateOf(parsed.statementDate ?: calculationDate) }
    var totalText by remember(parsed, manualTotal) { mutableStateOf(manualTotal ?: parsed.currentTotalHalfPoints?.let(::HalfPoints)?.asDisplayValue().orEmpty()) }
    val totalHalf = runCatching { totalText.toBigDecimal().multiply(2.toBigDecimal()).intValueExact() }.getOrNull()
    val context = LocalContext.current
    val existing by produceState<List<app.hubhelper.domain.AttendanceEvent>?>(null) {
        value = app.hubhelper.data.AttendanceRepository.create(context).allEvents()
    }
    val storedSetup by produceState<SetupData?>(null) {
        value = SetupStore(app.hubhelper.data.HubHelperDatabase.get(context)).read() ?: SetupData()
    }
    val opening = storedSetup?.attendanceOpeningRemainder?.toBigDecimalOrNull()?.multiply(2.toBigDecimal())?.toInt() ?: 0
    val calculated = existing?.let { app.hubhelper.domain.AttendanceCalculator().totalWithOpening(it, statementDate, HalfPoints(opening)).value }
    val validRows = edits.indices.all { index ->
        if (!accepted[index]) true else {
            val row = edits[index]
            val amount = row.adjustmentHalfPoints
            amount != null && amount != 0 && amount.toLong() in -10000L..10000L && !row.date.isAfter(statementDate) &&
                (row.reviewedType == null || (row.reviewedType == app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT) == (amount < 0))
        }
    }
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
                    Text("Your entered total is $manualTotal. The sheet total does not match. Verify the reported balance below before saving.", color = MaterialTheme.colorScheme.error)
                }
                DatePickerField("Statement balance through date", statementDate, { it?.let { value -> statementDate = value } })
                OutlinedTextField(totalText, { totalText = it }, label = { Text("Reported balance to reconcile") }, isError = totalHalf == null)
                Text("This records the reported balance through the selected date. Later events and future expirations change that balance. Undated remainder has no known expiration.")
                if (calculated != null && storedSetup != null) {
                    Text("Current calculated balance at that date: ${HalfPoints(calculated).asDisplayValue()}")
                    totalHalf?.let { Text("Reported minus calculated: ${HalfPoints(it - calculated).asDisplayValue()} points. Saving reconciles the remainder after adding the accepted rows.") }
                }
                if (statementDate.isAfter(calculationDate)) Text("Statement date cannot be in the future.", color = MaterialTheme.colorScheme.error)
                if (!validRows) Text("Accepted rows need a nonzero half-point amount, a matching charge/credit type, and a date on or before the statement date.", color = MaterialTheme.colorScheme.error)
                if (rows.isEmpty()) Text("No complete point rows were found. Nothing will be saved.")
                edits.forEachIndexed { index, row ->
                    val change = row.adjustmentHalfPoints?.let(::HalfPoints)?.asDisplayValue().orEmpty()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row {
                                androidx.compose.material3.Checkbox(accepted[index], { checked -> accepted = accepted.toMutableList().also { it[index] = checked } })
                                Text("Include row • Page ${row.sourcePageNumber ?: "unknown"}")
                            }
                            val overlaps = existing.orEmpty().filter { it.occurredOn == row.date && it.points.value.toLong() == kotlin.math.abs((row.adjustmentHalfPoints ?: 0).toLong()) }
                            if (overlaps.isNotEmpty()) Text("Review possible overlap: ${overlaps.size} existing record(s) have this date and amount. Uncheck this row if it describes an event already recorded from another source.", color = MaterialTheme.colorScheme.error)
                            sourceDocumentId?.let { SourceEvidenceButton(it, row.sourcePageNumber) }
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                app.hubhelper.domain.AttendanceEventType.entries.forEach { type ->
                                    androidx.compose.material3.FilterChip(row.reviewedType == type, {
                                        val amount = row.adjustmentHalfPoints?.let { if (type == app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT) -kotlin.math.abs(it) else kotlin.math.abs(it) }
                                        edits = edits.toMutableList().also { it[index] = row.copy(reviewedType = type, adjustmentHalfPoints = amount) }
                                    }, label = { Text(type.name.lowercase().replace('_', ' ')) })
                                }
                            }
                            DatePickerField("Event date", row.date, { date -> date?.let { edits = edits.toMutableList().also { list -> list[index] = row.copy(date = date) } } })
                            var changeText by remember(row.sourceRowKey, row.reviewedType) { mutableStateOf(change) }
                            OutlinedTextField(changeText, { text ->
                                changeText = text
                                val half = runCatching { text.toBigDecimal().multiply(2.toBigDecimal()).intValueExact() }.getOrNull()
                                edits = edits.toMutableList().also { it[index] = row.copy(adjustmentHalfPoints = half) }
                            }, label = { Text("Point change (negative for credit)") })
                            Text("Point change: $change   •   Running total: ${HalfPoints(row.runningTotalHalfPoints).asDisplayValue()}")
                            if (row.comment.isNotBlank()) Text(row.comment, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(parsed.copy(rows = edits.filterIndexed { i, _ -> accepted[i] }, currentTotalHalfPoints = totalHalf, statementDate = statementDate)) },
                enabled = totalHalf != null && totalHalf in -2..10000 && existing != null && storedSetup != null && !statementDate.isAfter(calculationDate) && edits.indices.any { accepted[it] } && validRows) { Text("Reconcile and save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DocumentViewerDialog(document: WorkDocument, initialPage: Int = 0, onDismiss: () -> Unit) {
    var page by remember(document.id) { mutableStateOf(initialPage) }
    var error by remember { mutableStateOf<String?>(null) }
    val count by produceState(0, document) {
        value = withContext(Dispatchers.IO) { runCatching { DocumentPages.count(document) }.getOrElse { error = it.message; 0 } }
    }
    val bitmap by produceState<Bitmap?>(null, document, page) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { DocumentPages.render(document, page) }.getOrElse { error = it.message; null } }
    }
    val context = LocalContext.current
    val pageText by produceState<String?>(null, document, page) {
        value = app.hubhelper.data.HubHelperDatabase.get(context).documentPageDao().pages(document.id).firstOrNull { it.pageNumber == page + 1 }?.text
    }
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offsetX by remember(page) { mutableFloatStateOf(0f) }
    var offsetY by remember(page) { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(8.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    Text("${if(count == 0) 0 else page + 1} / $count")
                    TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text("Next") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (bitmap != null) Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium)) {
                    androidx.compose.foundation.Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Original page ${page + 1}", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY).pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offsetX += pan.x; offsetY += pan.y }
                        })
                }
                Text("Page text", fontWeight = FontWeight.SemiBold)
                Column(Modifier.weight(0.65f).verticalScroll(rememberScrollState())) {
                    Text(pageText ?: "Page text has not been indexed. Use Read / retry OCR.")
                }
            }
        }
    }
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

private fun newCameraUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera-captures/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
internal fun SourceEvidenceButton(documentId: String, pageNumber: Int? = null) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val document by produceState<WorkDocument?>(null, documentId) { value = DocumentRepository.create(context).get(documentId) }
    if (document?.originalDeleted == true) Text("Source original deleted • $documentId", style = MaterialTheme.typography.bodySmall)
    else TextButton(onClick = { open = true }, enabled = document != null) { Text(if (document == null) "Source unavailable" else "Open evidence${pageNumber?.let { " • page $it" }.orEmpty()}") }
    if (open) document?.let { DocumentViewerDialog(it, (pageNumber ?: 1) - 1) { open = false } }
}
