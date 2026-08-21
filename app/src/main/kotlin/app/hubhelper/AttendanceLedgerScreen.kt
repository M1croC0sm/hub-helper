package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.TimeBalanceKind
import app.hubhelper.domain.TimeOffCalculator
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.CallInEvent
import app.hubhelper.domain.remainingCallIns
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.BookedTimeType
import app.hubhelper.domain.floatingHolidayAvailable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class RecordsSection { DETAILS, PTO, CALL_INS, SICK, HOLIDAYS, HISTORY }
private enum class AttendanceFilter(val label: String) { ALL("All"), CONFIRMED("Confirmed"), PENDING("Pending"), CREDITS("Credits") }

@Composable
fun AttendanceLedgerScreen(
    padding: PaddingValues,
    appDate: LocalDate,
    openingBalance: String,
    events: List<AttendanceEvent>,
    onAdd: (LocalDate, AttendanceEventType, HalfPoints, AttendanceEventStatus, String?) -> Unit,
    onDelete: (AttendanceEvent) -> Unit,
    onUpdate: (AttendanceEvent) -> Unit,
    timeAdjustments: List<TimeBalanceAdjustment>,
    callIns: List<CallInEvent>,
    callInsRemaining: Int,
    bookedPtoDays: List<BookedPtoDay>,
    ptoOpeningHours: String,
    sickOpeningHours: String,
    balancesAsOfDate: String,
    hireDate: String,
    shiftPreset: String?,
    birthdayMonth: Int?,
    onAddTimeAdjustment: (LocalDate, TimeBalanceKind, Int, String?) -> Unit,
    onDeleteTimeAdjustment: (TimeBalanceAdjustment) -> Unit,
    onDeleteCallIn: (CallInEvent) -> Unit,
    onDeleteBookedPto: (BookedPtoDay) -> Unit,
    holidays: List<PlantHoliday>,
    onAddHoliday: (LocalDate, String) -> Unit,
    onDeleteHoliday: (PlantHoliday) -> Unit,
    onViewRules: () -> Unit,
    initialSection: RecordsSection? = null,
) {
    val design = HubThemeDesign.tokens
    var showForm by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<AttendanceEvent?>(null) }
    var editCandidate by remember { mutableStateOf<AttendanceEvent?>(null) }
    var timeDeleteCandidate by remember { mutableStateOf<TimeBalanceAdjustment?>(null) }
    var callInDeleteCandidate by remember { mutableStateOf<CallInEvent?>(null) }
    var bookedPtoDeleteCandidate by remember { mutableStateOf<BookedPtoDay?>(null) }
    var attendanceFilter by remember { mutableStateOf(AttendanceFilter.ALL) }
    val detailsRequester = remember { BringIntoViewRequester() }
    val ptoRequester = remember { BringIntoViewRequester() }
    val callInRequester = remember { BringIntoViewRequester() }
    val sickRequester = remember { BringIntoViewRequester() }
    val holidayRequester = remember { BringIntoViewRequester() }
    val historyRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(initialSection) {
        when (initialSection) {
            RecordsSection.DETAILS -> detailsRequester
            RecordsSection.PTO -> ptoRequester
            RecordsSection.CALL_INS -> callInRequester
            RecordsSection.SICK -> sickRequester
            RecordsSection.HOLIDAYS -> holidayRequester
            RecordsSection.HISTORY -> historyRequester
            null -> null
        }?.bringIntoView()
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = design.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(design.contentSpacing),
    ) {
        AttendanceDetailsPanels(
            appDate,
            openingBalance,
            balancesAsOfDate,
            events,
            onViewRules,
            Modifier.bringIntoViewRequester(detailsRequester),
        )

        Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showForm) "Close event form" else "Add attendance event")
        }
        if (showForm) {
            AttendanceEventForm(appDate, null) { date, type, points, status, note ->
                onAdd(date, type, points, status, note)
                showForm = false
            }
        }
        editCandidate?.let { existing ->
            AttendanceEventForm(existing.occurredOn, existing) { date, type, points, status, note ->
                onUpdate(existing.copy(occurredOn = date, type = type, points = points, status = status, note = note))
                editCandidate = null
            }
        }

        TimeBalanceSection(
            appDate = appDate,
            adjustments = timeAdjustments,
            callIns = callIns,
            callInsRemaining = callInsRemaining,
            bookedPtoDays = bookedPtoDays,
            ptoOpeningHours = ptoOpeningHours,
            sickOpeningHours = sickOpeningHours,
            balancesAsOfDate = balancesAsOfDate,
            hireDate = hireDate,
            shiftPreset = shiftPreset,
            birthdayMonth = birthdayMonth,
            onAdd = onAddTimeAdjustment,
            onDelete = { timeDeleteCandidate = it },
            onDeleteCallIn = { callInDeleteCandidate = it },
            ptoRequester = ptoRequester,
            callInRequester = callInRequester,
            sickRequester = sickRequester,
        )
        BookedPtoSection(bookedPtoDays) { bookedPtoDeleteCandidate = it }
        HolidaySection(
            holidays = holidays,
            modifier = Modifier.bringIntoViewRequester(holidayRequester),
        )

        Text(
            "Records — Attendance",
            modifier = Modifier.bringIntoViewRequester(historyRequester),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttendanceFilter.entries.forEach { filter ->
                FilterChip(
                    selected = attendanceFilter == filter,
                    onClick = { attendanceFilter = filter },
                    label = { Text(filter.label) },
                )
            }
        }
        val filteredEvents = events.filter { event ->
            when (attendanceFilter) {
                AttendanceFilter.ALL -> true
                AttendanceFilter.CONFIRMED -> event.status == AttendanceEventStatus.CONFIRMED
                AttendanceFilter.PENDING -> event.status == AttendanceEventStatus.PENDING
                AttendanceFilter.CREDITS -> event.type == AttendanceEventType.ATTENDANCE_CREDIT
            }
        }.sortedByDescending { it.occurredOn }
        if (filteredEvents.isEmpty()) {
            Text(if (events.isEmpty()) "No dated attendance events yet." else "No records match this filter.")
        } else {
            filteredEvents.groupBy { YearMonth.from(it.occurredOn) }.forEach { (month, monthEvents) ->
                SectionLabel(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = MaterialTheme.colorScheme.primary)
                monthEvents.forEach { event ->
                    AttendanceEventCard(
                        event,
                        AttendanceCalculator().expiresOn(event),
                        onEdit = { editCandidate = event; showForm = false },
                        onDelete = { deleteCandidate = event },
                    )
                }
            }
        }
    }

    deleteCandidate?.let { event ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete attendance event?") },
            text = { Text("This removes the ${event.occurredOn} event from calculations. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(event)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }
    timeDeleteCandidate?.let { adjustment ->
        AlertDialog(
            onDismissRequest = { timeDeleteCandidate = null },
            title = { Text("Delete time-balance entry?") },
            text = { Text("This removes the ${adjustment.occurredOn} adjustment from the balance.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTimeAdjustment(adjustment)
                    timeDeleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { timeDeleteCandidate = null }) { Text("Cancel") } },
        )
    }
    callInDeleteCandidate?.let { callIn ->
        AlertDialog(
            onDismissRequest = { callInDeleteCandidate = null },
            title = { Text("Delete call-in day?") },
            text = { Text("This restores one call-in for ${callIn.occurredOn.year} and returns ${callIn.ptoMinutes / 60} hours to the calculated PTO balance.") },
            confirmButton = {
                TextButton(onClick = { onDeleteCallIn(callIn); callInDeleteCandidate = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { callInDeleteCandidate = null }) { Text("Cancel") } },
        )
    }
    bookedPtoDeleteCandidate?.let { day ->
        AlertDialog(
            onDismissRequest = { bookedPtoDeleteCandidate = null },
            title = { Text("Remove booked PTO date?") },
            text = { Text("This removes ${day.date.monthDayYear()} from the booked-vacation tracker. It does not change your PTO balance.") },
            confirmButton = {
                TextButton(onClick = { onDeleteBookedPto(day); bookedPtoDeleteCandidate = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { bookedPtoDeleteCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AttendanceDetailsPanels(
    appDate: LocalDate,
    openingBalance: String,
    balancesAsOfDate: String,
    events: List<AttendanceEvent>,
    onViewRules: () -> Unit,
    modifier: Modifier = Modifier,
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

    HubPanel(modifier.fillMaxWidth(), accent = risk.third) {
        SectionLabel("Current total")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricValue(totalText, "POINTS", risk.third)
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                StatusBadge(risk.first, risk.third)
                Text(risk.second, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    HubPanel(Modifier.fillMaxWidth()) {
        SectionLabel("How this total is calculated")
        CalculationRow("Opening balance (as of ${runCatching { LocalDate.parse(balancesAsOfDate).monthDayYear() }.getOrDefault(balancesAsOfDate)})", opening.stripTrailingZeros().toPlainString())
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
private fun BookedPtoSection(days: List<BookedPtoDay>, onDelete: (BookedPtoDay) -> Unit) {
    Text("Booked PTO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (days.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Text("No vacation dates booked. Add one from Log or scan a PTO exception form in Documents.", Modifier.padding(16.dp))
        }
    }
    days.forEach { day ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day.date.monthDayYear(), fontWeight = FontWeight.SemiBold)
                Text(when (day.type) {
                    app.hubhelper.domain.BookedTimeType.REGULAR_PTO -> "Regular PTO"
                    app.hubhelper.domain.BookedTimeType.BIRTHDAY_FLOATING -> "Birthday-month floating holiday"
                    app.hubhelper.domain.BookedTimeType.ANYTIME_FLOATING -> "Anytime floating holiday"
                })
                ProvenanceBadge(if (day.sourceDocumentId == null) "User" else "Source")
                OutlinedButton(onClick = { onDelete(day) }) { Text("Remove") }
            }
        }
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

@Composable
private fun AttendanceEventCard(
    event: AttendanceEvent,
    expiration: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = if (event.status == AttendanceEventStatus.CONFIRMED) HubThemeDesign.tokens.good else MaterialTheme.colorScheme.onSurfaceVariant
    HubPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.occurredOn.monthDayYear(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.1f))
                Text(attendanceTypeLabel(event.type), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.5f))
                Text(
                    (if (event.type == AttendanceEventType.ATTENDANCE_CREDIT) "−" else "+") + event.points.asDisplayValue(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(event.status.name.lowercase().replaceFirstChar(Char::uppercase), color = statusColor, style = MaterialTheme.typography.labelMedium)
            Text(
                if (event.type == AttendanceEventType.ATTENDANCE_CREDIT) "Credit adjustment; no annual falloff"
                else "12-month falloff: ${expiration.monthDayYear()}",
                style = MaterialTheme.typography.bodySmall,
            )
            event.note?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun attendanceTypeLabel(type: AttendanceEventType): String = when (type) {
    AttendanceEventType.UNEXCUSED_ABSENCE -> "Absence"
    AttendanceEventType.TARDY -> "Tardy"
    AttendanceEventType.LEFT_EARLY -> "Left early"
    AttendanceEventType.CALL_IN_VIOLATION -> "Call-in violation"
    AttendanceEventType.ATTENDANCE_CREDIT -> "Attendance credit"
}

@Composable
private fun TimeBalanceSection(
    appDate: LocalDate,
    adjustments: List<TimeBalanceAdjustment>,
    callIns: List<CallInEvent>,
    callInsRemaining: Int,
    bookedPtoDays: List<BookedPtoDay>,
    ptoOpeningHours: String,
    sickOpeningHours: String,
    balancesAsOfDate: String,
    hireDate: String,
    shiftPreset: String?,
    birthdayMonth: Int?,
    onAdd: (LocalDate, TimeBalanceKind, Int, String?) -> Unit,
    onDelete: (TimeBalanceAdjustment) -> Unit,
    onDeleteCallIn: (CallInEvent) -> Unit,
    ptoRequester: BringIntoViewRequester,
    callInRequester: BringIntoViewRequester,
    sickRequester: BringIntoViewRequester,
) {
    var showForm by remember { mutableStateOf(false) }

    fun balance(opening: String, kind: TimeBalanceKind): String {
        return TimeOffCalculator.balanceHours(
            kind = kind,
            enteredBalanceHours = opening,
            enteredBalanceDate = runCatching { LocalDate.parse(balancesAsOfDate) }.getOrDefault(appDate),
            hireDate = runCatching { LocalDate.parse(hireDate) }.getOrNull(),
            asOf = appDate,
            adjustments = adjustments,
            callIns = callIns,
            bookedPtoDays = bookedPtoDays,
            regularBookedPtoMinutes = if (shiftPreset == "SECOND") 10 * 60 else 8 * 60,
        )
    }

    Text("PTO", modifier = Modifier.bringIntoViewRequester(ptoRequester), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("PTO: ${balance(ptoOpeningHours, TimeBalanceKind.PTO)} hours")
        }
    }
    Text("Call-ins", modifier = Modifier.bringIntoViewRequester(callInRequester), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$callInsRemaining of 5 remaining for ${appDate.year}", fontWeight = FontWeight.SemiBold)
            Text("Each call-in is an excused day and deducts the recorded shift-day amount from PTO.", style = MaterialTheme.typography.bodySmall)
        }
    }
    callIns.filter { it.occurredOn.year == appDate.year }.forEach { event ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${event.occurredOn} • Call-in day", fontWeight = FontWeight.SemiBold)
                Text("Excused • −${event.ptoMinutes / 60} PTO hours")
                OutlinedButton(onClick = { onDeleteCallIn(event) }) { Text("Delete") }
            }
        }
    }
    Text("Sick time", modifier = Modifier.bringIntoViewRequester(sickRequester), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val sickDays = sickDaysFromHours(balance(sickOpeningHours, TimeBalanceKind.SICK))
            Text("Sick: $sickDays ${dayLabel(sickDays)}")
        }
    }
    OutlinedButton(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showForm) "Close time form" else "Record PTO or sick time used")
    }
    if (showForm) {
        TimeBalanceForm(appDate, if (shiftPreset == "SECOND") 10 else 8, birthdayMonth, adjustments, bookedPtoDays) { date, kind, minutes, note ->
            onAdd(date, kind, minutes, note)
            showForm = false
        }
    }
    adjustments.forEach { adjustment ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${adjustment.occurredOn} • ${adjustment.kind.name}", fontWeight = FontWeight.SemiBold)
                val absoluteHours = formatMinutesAsHours(kotlin.math.abs(adjustment.minutes))
                val amount = if (adjustment.kind == TimeBalanceKind.SICK) {
                    val days = sickDaysFromHours(absoluteHours)
                    "$days ${dayLabel(days)}"
                } else "$absoluteHours hours"
                Text(if (adjustment.minutes < 0) "$amount used" else "+$amount correction")
                adjustment.note?.let { Text(it) }
                OutlinedButton(onClick = { onDelete(adjustment) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun TimeBalanceForm(
    initialDate: LocalDate,
    ptoFullDayHours: Int,
    birthdayMonth: Int?,
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
    onSave: (LocalDate, TimeBalanceKind, Int, String?) -> Unit,
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var kind by remember { mutableStateOf(TimeBalanceKind.PTO) }
    var ptoType by remember { mutableStateOf(BookedTimeType.REGULAR_PTO) }
    var hoursText by remember { mutableStateOf("") }
    var showOtherAmount by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val decimalAmount = hoursText.toBigDecimalOrNull()
    val floating = kind == TimeBalanceKind.PTO && ptoType != BookedTimeType.REGULAR_PTO
    val exactMinutes = if (floating) BigDecimal(480) else decimalAmount?.multiply(BigDecimal(if (kind == TimeBalanceKind.SICK) 480 else 60))
    val minutes = exactMinutes?.toInt()
    val birthdayDateValid = ptoType != BookedTimeType.BIRTHDAY_FLOATING || birthdayMonth == selectedDate.monthValue
    val floatingAvailable = !floating || floatingHolidayAvailable(ptoType, adjustments, bookedDays, selectedDate.year)
    val amountValid = minutes != null && minutes > 0 && exactMinutes.compareTo(BigDecimal(minutes)) == 0 && birthdayDateValid && floatingAvailable

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DatePickerField("Date used", selectedDate, { it?.let { chosen -> selectedDate = chosen } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TimeBalanceKind.PTO, TimeBalanceKind.SICK).forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { kind = option; hoursText = ""; showOtherAmount = false },
                        label = { Text(option.name) },
                    )
                }
            }
            if (kind == TimeBalanceKind.PTO) {
                listOf(
                    BookedTimeType.REGULAR_PTO to "Regular PTO",
                    BookedTimeType.BIRTHDAY_FLOATING to "Birthday floating",
                    BookedTimeType.ANYTIME_FLOATING to "Anytime floating",
                ).forEach { (option, label) ->
                    FilterChip(selected = ptoType == option, onClick = { ptoType = option; hoursText = ""; showOtherAmount = false }, label = { Text(label) })
                }
            }
            if (kind == TimeBalanceKind.PTO && !floating) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { hoursText = ptoFullDayHours.toString(); showOtherAmount = false }) {
                        Text("Full day ($ptoFullDayHours h)")
                    }
                    OutlinedButton(onClick = { hoursText = (ptoFullDayHours / 2).toString(); showOtherAmount = false }) {
                        Text("Half day (${ptoFullDayHours / 2} h)")
                    }
                }
            } else if (kind == TimeBalanceKind.SICK) {
                Button(onClick = { hoursText = "1"; showOtherAmount = false }) {
                    Text("Use 1 sick day")
                }
            }
            if (!floating) OutlinedButton(onClick = { showOtherAmount = true; hoursText = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("Other amount")
            }
            if (floating) {
                Text("One floating holiday • 8 hours", fontWeight = FontWeight.SemiBold)
                if (!birthdayDateValid) Text("Birthday floating must be used during your birthday month.", color = MaterialTheme.colorScheme.error)
                if (!floatingAvailable) Text("That floating holiday is already used or booked for this year.", color = MaterialTheme.colorScheme.error)
            } else if (showOtherAmount) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text(if (kind == TimeBalanceKind.SICK) "Sick days used" else "Hours used") },
                    placeholder = { Text(if (kind == TimeBalanceKind.SICK) "Example: 1" else "Example: 2") },
                    isError = hoursText.isNotBlank() && !amountValid,
                    supportingText = { Text("Enter a positive number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (amountValid) {
                Text("Selected: $hoursText ${if (kind == TimeBalanceKind.SICK) dayLabel(hoursText) else "hours"}", fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val savedKind = if (kind == TimeBalanceKind.SICK) TimeBalanceKind.SICK else when (ptoType) {
                        BookedTimeType.BIRTHDAY_FLOATING -> TimeBalanceKind.FLOATING_BIRTHDAY
                        BookedTimeType.ANYTIME_FLOATING -> TimeBalanceKind.FLOATING_ANYTIME
                        BookedTimeType.REGULAR_PTO -> TimeBalanceKind.PTO
                    }
                    onSave(selectedDate, savedKind, -minutes!!, note.ifBlank { null })
                },
                enabled = amountValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (floating) "Record floating holiday (8 hours)" else if (amountValid) "Record $hoursText ${if (kind == TimeBalanceKind.SICK) dayLabel(hoursText) else "${kind.name} hours"}" else "Record time used") }
        }
    }
}

private fun formatMinutesAsHours(minutes: Int): String =
    BigDecimal(minutes).divide(BigDecimal(60)).stripTrailingZeros().toPlainString()

@Composable
private fun HolidaySection(
    holidays: List<PlantHoliday>,
    modifier: Modifier = Modifier,
) {
    Text("Holidays", modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan the annual holiday sheet in Documents using the Holiday calendar category.")
            if (holidays.isEmpty()) Text("No reviewed holiday calendar has been loaded yet.")
        }
    }
    holidays.forEach { holiday ->
        Card(Modifier.fillMaxWidth()) {
            Text("${holiday.date} • ${holiday.name}", modifier = Modifier.padding(16.dp))
        }
    }
}
