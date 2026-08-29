package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.HalfPoints
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun AttendanceDetailsScreen(
    padding: PaddingValues,
    appDate: LocalDate,
    openingBalance: String,
    balancesAsOfDate: String,
    events: List<AttendanceEvent>,
    onViewRules: () -> Unit,
) {
    Column(
        Modifier
            .padding(padding)
            .padding(horizontal = HubThemeDesign.tokens.screenPadding, vertical = HubThemeDesign.tokens.contentSpacing)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HubThemeDesign.tokens.contentSpacing),
    ) {
        AttendanceDetailsPanels(appDate, openingBalance, balancesAsOfDate, events, onViewRules)
    }
}

@Composable
private fun AttendanceDetailsPanels(
    appDate: LocalDate,
    openingBalance: String,
    balancesAsOfDate: String,
    events: List<AttendanceEvent>,
    onViewRules: () -> Unit,
) {
    val calculator = remember { AttendanceCalculator() }
    val summary = remember(events, appDate) { calculator.summarize(events, appDate) }
    val breakdown = remember(events, appDate) { calculator.breakdown(events, appDate) }
    val opening = openingBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val datedNet = BigDecimal(summary.confirmedPoints.value).divide(BigDecimal(2))
    val total = opening.add(datedNet)
    val totalText = total.stripTrailingZeros().toPlainString()
    val design = HubThemeDesign.tokens
    val risk = when (app.hubhelper.domain.attendanceColorBand(total)) {
        app.hubhelper.domain.AttendanceColorBand.GREEN -> Triple("Low risk", "Good standing", design.good)
        app.hubhelper.domain.AttendanceColorBand.ORANGE -> Triple("Watch", "Review upcoming changes", design.attention)
        app.hubhelper.domain.AttendanceColorBand.RED -> Triple("High risk", "Attendance points above five", MaterialTheme.colorScheme.error)
    }
    val nextExpirationAmount = summary.nextExpirationDate?.let { date ->
        breakdown.includedEvents.filter {
            it.type != AttendanceEventType.ATTENDANCE_CREDIT && calculator.expiresOn(it) == date
        }.sumOf { it.points.value }
    }
    val nextCredit = remember(events, appDate) { calculator.nextAttendanceCreditDate(events, appDate) }

    HubPanel(Modifier.fillMaxWidth(), accent = risk.third) {
        SectionLabel("Current total")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricValue(totalText, "POINTS", risk.third)
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(risk.first, risk.third)
                Text(risk.second, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    HubPanel(Modifier.fillMaxWidth()) {
        SectionLabel("How this total is calculated")
        CalculationRow(
            "Opening balance (as of ${runCatching { LocalDate.parse(balancesAsOfDate).monthDayYear() }.getOrDefault(balancesAsOfDate)})",
            opening.stripTrailingZeros().toPlainString(),
        )
        CalculationRow("Confirmed charges", "+${breakdown.confirmedCharges.asDisplayValue()}")
        CalculationRow("Confirmed credits", "−${breakdown.confirmedCredits.asDisplayValue()}")
        HorizontalDivider(Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        CalculationRow("Total", totalText, emphasized = true)
        Text(
            "${breakdown.includedEvents.size} included • ${breakdown.excludedEvents.size} informational or excluded • ${summary.pendingEventCount} pending",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (openingBalance.isNotBlank()) {
            Text("The manually entered opening balance has no individual dates, so its falloffs remain unknown.", style = MaterialTheme.typography.bodySmall)
        }
    }
    HubPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Upcoming")
        CalculationRow(
            summary.nextExpirationDate?.monthDayYear() ?: "No dated falloff",
            nextExpirationAmount?.let { "−${HalfPoints(it).asDisplayValue()}" }.orEmpty(),
            emphasized = summary.nextExpirationDate != null,
        )
        if (summary.nextExpirationDate != null) Text("Point expires", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        SectionLabel("Estimated 90-day credit", color = design.pto)
        CalculationRow(nextCredit?.monthDayYear() ?: "Unknown", "Estimated")
    }
    OutlinedButton(onClick = onViewRules, modifier = Modifier.fillMaxWidth()) {
        Text("VIEW SOURCE & RULES  ›")
    }
}

@Composable
private fun CalculationRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = if (emphasized) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (emphasized) HubThemeDesign.tokens.metricMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize) else MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun AttendanceEventForm(
    initialDate: LocalDate,
    initialEvent: AttendanceEvent?,
    onSave: (LocalDate, AttendanceEventType, HalfPoints, AttendanceEventStatus, String?) -> Unit,
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var type by remember(initialEvent) { mutableStateOf(initialEvent?.type ?: AttendanceEventType.UNEXCUSED_ABSENCE) }
    var pointText by remember(initialEvent) { mutableStateOf(initialEvent?.points?.asDisplayValue() ?: "1") }
    var status by remember(initialEvent) { mutableStateOf(initialEvent?.status ?: AttendanceEventStatus.CONFIRMED) }
    var note by remember(initialEvent) { mutableStateOf(initialEvent?.note.orEmpty()) }
    val parsedPoints = pointText.toBigDecimalOrNull()
    val halfPoints = parsedPoints?.multiply("2".toBigDecimal())?.toInt()
    val pointsValid = halfPoints != null && halfPoints >= 0 &&
        parsedPoints.compareTo(halfPoints.toBigDecimal().divide("2".toBigDecimal())) == 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DatePickerField("Date", selectedDate, { it?.let { chosen -> selectedDate = chosen } })
            Text("Event type", style = MaterialTheme.typography.labelLarge)
            ChoiceRows(
                choices = AttendanceEventType.entries,
                selected = type,
                label = {
                    when (it) {
                        AttendanceEventType.UNEXCUSED_ABSENCE -> "Absence"
                        AttendanceEventType.TARDY -> "Tardy"
                        AttendanceEventType.LEFT_EARLY -> "Left early"
                        AttendanceEventType.CALL_IN_VIOLATION -> "Call-in"
                        AttendanceEventType.ATTENDANCE_CREDIT -> "90-day credit"
                    }
                },
                onSelected = {
                    type = it
                    pointText = when (it) {
                        AttendanceEventType.UNEXCUSED_ABSENCE, AttendanceEventType.ATTENDANCE_CREDIT -> "1"
                        else -> "0.5"
                    }
                },
            )
            OutlinedTextField(
                value = pointText,
                onValueChange = { pointText = it },
                label = { Text("Points") },
                isError = !pointsValid,
                supportingText = { Text("Review the value before saving") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Status", style = MaterialTheme.typography.labelLarge)
            ChoiceRows(
                choices = AttendanceEventStatus.entries,
                selected = status,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelected = { status = it },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Optional note") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(selectedDate, type, HalfPoints(halfPoints!!), status, note.ifBlank { null }) },
                enabled = pointsValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (initialEvent == null) "Save event" else "Save changes") }
        }
    }
}

@Composable
private fun <T> ChoiceRows(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    choices.chunked(2).forEach { rowChoices ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowChoices.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onSelected(choice) },
                    label = { Text(label(choice)) },
                )
            }
        }
    }
}
