package app.hubhelper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import app.hubhelper.domain.PlantHoliday

private data class BundledReference(val title: String, val assetName: String, val sourceNote: String)
internal data class ReferenceSearchMatch(val referenceIndex: Int, val lineIndex: Int, val line: String)
private data class SelectedReference(val referenceIndex: Int, val targetLineIndex: Int? = null)

@Composable
fun ContractLibraryScreen(padding: PaddingValues, holidays: List<PlantHoliday> = emptyList()) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val references = remember {
        listOf(
            BundledReference(
                "Collective Bargaining Agreement 2025–2029",
                "Hubbell_Killark_CBA_2025-2029.md",
                "Collective bargaining agreement effective May 1, 2025 through April 30, 2029.",
            ),
            BundledReference(
                "Light Industrial Attendance Policy",
                "Light_Industrial_Attendance_Policy.md",
                "Attendance policy with separately identified supplemental supported rules at the bottom.",
            ),
        ).map { reference -> reference to context.assets.open(reference.assetName).bufferedReader().use { it.readText() } }
    }
    var query by remember { mutableStateOf("") }
    var selectedReference by remember { mutableStateOf<SelectedReference?>(null) }
    var showSearchResults by remember { mutableStateOf(false) }
    var showAllHolidays by remember { mutableStateOf(false) }
    val matches = remember(query, references) { searchReferenceLines(references.map { it.second }, query) }

    selectedReference?.let { selected ->
        BackHandler { selectedReference = null }
        ReferenceReader(
            padding = padding,
            reference = references[selected.referenceIndex],
            targetLineIndex = selected.targetLineIndex,
            onBack = { selectedReference = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reference", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Search your contract, policies and plant calendar.", style = MaterialTheme.typography.bodyLarge)
        Text("OFFLINE REFERENCE LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; showSearchResults = false },
            label = { Text("Search reference…") },
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
            trailingIcon = {
                TextButton(onClick = { keyboard?.hide(); showSearchResults = true }, enabled = query.length >= 2) { Text("GO") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); showSearchResults = true }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (showSearchResults) {
            HubPanel(Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
                SectionLabel("Search results")
                Text("${matches.size} result${if (matches.size == 1) "" else "s"} for “$query”")
                if (matches.isEmpty()) Text("No matching passages were found.")
                matches.forEach { match ->
                    val reference = references[match.referenceIndex].first
                    TextButton(
                        onClick = { selectedReference = SelectedReference(match.referenceIndex, match.lineIndex) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(reference.title, style = MaterialTheme.typography.labelLarge)
                            Text(match.line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        references.forEachIndexed { index, referenceWithText ->
            val reference = referenceWithText.first
            Card(
                Modifier.fillMaxWidth().clickable { selectedReference = SelectedReference(index) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▤", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(reference.title, fontWeight = FontWeight.SemiBold)
                        Text(reference.sourceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLANT CALENDAR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (holidays.isEmpty()) {
                    Text("No reviewed holidays loaded. Add the annual calendar in Documents.")
                } else {
                    val next = holidays.firstOrNull { !it.date.isBefore(java.time.LocalDate.now()) } ?: holidays.first()
                    Text("Next plant holiday", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(next.date.monthDayYear(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(next.name, style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { showAllHolidays = !showAllHolidays }) {
                        Text(if (showAllHolidays) "Hide all holidays" else "View all holidays →")
                    }
                    if (showAllHolidays) {
                        holidays.forEach { holiday -> Text("${holiday.date.monthDayYear()} • ${holiday.name}") }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("USER MANUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("How do I use Hub Helper?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ReferenceQna("How do I get started?", "To get started, enter your hire date, shift, balances, call-ins, and current attendance points in setup. You can edit these values later in Settings.")
                ReferenceQna("How do I record something that happened?", "To record an event, tap LOG, choose the event type, enter the date and details, then save it.")
                ReferenceQna("How do I scan an attendance sheet?", "To scan an attendance sheet, open Documents, tap Add document, choose Attendance, select all pages, review the detected dates and point changes, then confirm and save.")
                ReferenceQna("How do I view a saved document?", "To view a saved document, open Documents and tap View document and OCR. Pinch to zoom and drag the image while zoomed.")
                ReferenceQna("How do I understand point falloff?", "To understand point falloff, open Calendar or Attendance details. Confirmed dated attendance records fall off individually after their rolling period; the manually entered current total remains authoritative.")
                ReferenceQna("How do I use the calendar?", "To use the calendar, tap Calendar, choose a month, then tap a day to see its events. Use the always-visible legend to understand each symbol and color.")
                ReferenceQna("How do I search the contract or policy?", "To search the references, enter at least two characters in Search reference and tap GO or the keyboard Search action. Tap a result to open the matching passage.")
                ReferenceQna("How do I add a company holiday?", "To add a company holiday, scan or select a holiday calendar in Documents and review the detected dates before saving. Contract holidays are generated automatically; scanned calendars can add exceptions.")
                ReferenceQna("How do I change the theme?", "To change the theme, open Settings, choose Industrial Instrument, Clear & Easy, or Soft & Friendly, then choose Follow system, Light, or Dark.")
                ReferenceQna("How do I reset the app?", "To reset the app, open Settings, tap Reset app to zero, and confirm. This clears records and documents, zeros balances, and returns to Settings.")
                ReferenceQna("How do I protect or back up my data?", "To protect or back up your data, enable app lock in Settings and use Export private backup. Store exported files securely because they are outside the app’s private storage.")
            }
        }
    }
}

@Composable
private fun ReferenceQna(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(question, fontWeight = FontWeight.SemiBold)
        Text(answer, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun searchReferenceLines(documents: List<String>, query: String): List<ReferenceSearchMatch> {
    if (query.length < 2) return emptyList()
    return documents.flatMapIndexed { referenceIndex, text ->
        text.lines().withIndex()
            .filter { it.value.contains(query, ignoreCase = true) }
            .take(12)
            .map { ReferenceSearchMatch(referenceIndex, it.index, it.value.trim()) }
    }
}

@Composable
private fun ReferenceReader(
    padding: PaddingValues,
    reference: Pair<BundledReference, String>,
    targetLineIndex: Int?,
    onBack: () -> Unit,
) {
    val lines = remember(reference.second) { reference.second.lines() }
    val listState = rememberLazyListState()
    LaunchedEffect(reference.first.assetName, targetLineIndex) {
        targetLineIndex?.let { listState.scrollToItem(it + 2) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.padding(padding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) {
                Text(if (targetLineIndex == null) "Back to reference library" else "Back to search results")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reference.first.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(reference.first.sourceNote, style = MaterialTheme.typography.bodySmall)
            }
        }
        itemsIndexed(lines) { index, line ->
            ReferenceDocumentLine(
                line = line,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == targetLineIndex) Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp)
                        else Modifier
                    ),
                color = if (index == targetLineIndex) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (line.startsWith("#")) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ReferenceDocumentLine(line: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color, fontWeight: FontWeight) {
    val trimmed = line.trim()
    val headingLevel = trimmed.takeWhile { it == '#' }.length
    val display = trimmed
        .removePrefix("### ").removePrefix("## ").removePrefix("# ")
        .replace("**", "")
        .replace("`", "")
        .ifBlank { " " }
    val style = when (headingLevel) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyLarge
    }
    Text(
        text = display,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = if (headingLevel > 0 || line.startsWith("|") || line.startsWith(">")) FontWeight.SemiBold else fontWeight,
    )
}
