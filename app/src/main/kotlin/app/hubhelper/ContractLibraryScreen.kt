package app.hubhelper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
        Text("Offline reference library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Search the current contract and attendance policy. Results open directly at the matching passage. Your reviewed plant holidays are listed below.")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; showSearchResults = false },
            label = { Text("Search contract and policy") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); showSearchResults = true }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { keyboard?.hide(); showSearchResults = true },
            enabled = query.length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("SEARCH REFERENCE") }
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(reference.title, fontWeight = FontWeight.SemiBold)
                    Text(reference.sourceNote, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { selectedReference = SelectedReference(index) }) {
                        Text("Read full document")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plant holidays", fontWeight = FontWeight.SemiBold)
                Text("Reviewed dates imported from your annual holiday calendar.", style = MaterialTheme.typography.bodySmall)
                if (holidays.isEmpty()) {
                    Text("No reviewed holidays loaded. Add the annual calendar in Documents.")
                } else {
                    holidays.forEach { holiday ->
                        Text("${holiday.date.monthDayYear()} • ${holiday.name}")
                    }
                }
            }
        }
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
            Text(
                text = line.ifBlank { " " },
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
