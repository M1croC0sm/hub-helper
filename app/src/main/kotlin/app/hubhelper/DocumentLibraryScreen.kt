package app.hubhelper

import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.AttendancePrintoutParser
import app.hubhelper.domain.ParsedAttendanceStatement
import app.hubhelper.domain.WorkDocument
import app.hubhelper.domain.WorkNote
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
    val keyboard = LocalSoftwareKeyboardController.current
    var category by remember { mutableStateOf<DocumentCategory?>(null) }
    var showAddDocument by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedSearchDocument by remember { mutableStateOf<WorkDocument?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<WorkDocument?>(null) }
    var noteText by remember { mutableStateOf("") }
    var noteDeleteCandidate by remember { mutableStateOf<WorkNote?>(null) }
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
    val searchMatches = documents.filter { document ->
        query.isBlank() || document.title.contains(query, ignoreCase = true) ||
            document.ocrText?.contains(query, ignoreCase = true) == true
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
        if (documents.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find something in saved documents") },
                placeholder = { Text("Example: vacation") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); showSearchResults = true }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { keyboard?.hide(); showSearchResults = true },
                enabled = query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("SEARCH SAVED DOCUMENTS") }
            if (showSearchResults) {
                IndustrialPanel(Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
                    SectionLabel("Search results")
                    Text("${searchMatches.size} result${if (searchMatches.size == 1) "" else "s"} for “$query”")
                    if (searchMatches.isEmpty()) Text("Nothing matched saved titles or detected text.")
                    searchMatches.forEach { document ->
                        TextButton(
                            onClick = { selectedSearchDocument = document },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(document.title, fontWeight = FontWeight.SemiBold)
                                Text(friendlyCategory(document.category), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
                    val recognizedText = document.ocrText
                    if (!recognizedText.isNullOrBlank()) {
                        OutlinedButton(onClick = {
                            expandedId = if (expandedId == document.id) null else document.id
                        }) { Text(if (expandedId == document.id) "Hide readable text" else "Read detected text") }
                        if (expandedId == document.id) Text(recognizedText)
                        if (document.category == DocumentCategory.ATTENDANCE) {
                            val parsed = remember(recognizedText) { AttendancePrintoutParser().parse(recognizedText) }
                            Text("${parsed.rows.size} dated rows recognized. These dates are used only to calculate point falloffs.")
                            Text("Set your current point total manually in Settings.", fontWeight = FontWeight.SemiBold)
                            if (parsed.rows.isNotEmpty()) {
                                Button(onClick = { onApplyAttendanceStatement(document, parsed) }) {
                                    Text("Use dated entries for falloffs")
                                }
                            }
                            parsed.warnings.forEach { Text("Review: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (document.category == DocumentCategory.HOLIDAY_CALENDAR) {
                            val parsed = remember(recognizedText, appDate.year) { HolidayCalendarParser().parse(recognizedText, appDate.year) }
                            SectionLabel("Detected holidays", color = Color(0xFFD9BF66))
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
                            SectionLabel("Detected booked dates", color = HubColors.Blue)
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
                        DocumentCategory.entries.forEach { option ->
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

    selectedSearchDocument?.let { document ->
        AlertDialog(
            onDismissRequest = { selectedSearchDocument = null },
            title = { Text(document.title) },
            text = {
                Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProvenanceBadge(if (document.ocrText.isNullOrBlank()) "Source" else "OCR")
                    Text(friendlyCategory(document.category))
                    Text(document.ocrText?.takeIf(String::isNotBlank) ?: "No searchable text is available. The original remains saved.")
                }
            },
            confirmButton = { TextButton(onClick = { selectedSearchDocument = null }) { Text("Done") } },
        )
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
}

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

private fun friendlyTextStatus(document: WorkDocument): String = when (document.ocrStatus.name) {
    "COMPLETE" -> "text ready"
    "PROCESSING" -> "reading text"
    "FAILED" -> "could not read text"
    "UNSUPPORTED" -> "saved original"
    else -> "waiting to read text"
}
