package app.hubhelper

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.TimeBalanceKind
import app.hubhelper.domain.CallInEvent
import app.hubhelper.domain.remainingCallIns
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.BookedTimeType
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.floatingHolidayAvailable
import java.math.BigDecimal
import java.time.LocalDate
import java.io.File
import java.util.UUID

private enum class LogStep { MENU, ATTENDANCE, CALL_IN, TIME, BOOKED_PTO, MANAGE_FLOATING, NOTE, DOCUMENT, WEEK_DONE }

@Composable
fun GlobalLogDialog(
    appDate: LocalDate,
    shiftPreset: String?,
    onDismiss: () -> Unit,
    onAttendance: (LocalDate, AttendanceEventType, HalfPoints, AttendanceEventStatus, String?) -> Unit,
    onTimeUsed: (LocalDate, TimeBalanceKind, Int, String?, String?) -> Unit,
    callIns: List<CallInEvent>,
    callInsRemainingForYear: (Int) -> Int,
    onCallIn: (LocalDate, Int, String?) -> Unit,
    bookedPtoDays: List<BookedPtoDay>,
    timeAdjustments: List<TimeBalanceAdjustment>,
    birthdayMonth: Int?,
    floatingAllowance: Int,
    onBookPto: (LocalDate, BookedTimeType, Int, app.hubhelper.domain.BookingStatus) -> Unit,
    onDeleteTimeAdjustment: (TimeBalanceAdjustment) -> Unit,
    onDeleteBookedPto: (BookedPtoDay) -> Unit,
    onNote: (LocalDate, String) -> Unit,
    onDocument: (Uri, DocumentCategory) -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(LogStep.MENU) }
    var documentCategory by rememberSaveable { mutableStateOf(DocumentCategory.ATTENDANCE) }
    val cameraFile = remember {
        File(context.cacheDir, "camera-captures/quick-log-${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    }
    val cameraUri = remember(cameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onDocument(it, documentCategory) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            onDocument(cameraUri, documentCategory)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (step == LogStep.MENU) onDismiss() else step = LogStep.MENU }) {
                        Text(if (step == LogStep.MENU) "Close" else "‹ Back")
                    }
                    Text(
                        if (step == LogStep.MENU) "LOG EVENT" else logStepTitle(step),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = HubThemeDesign.tokens.screenPadding, vertical = 10.dp),
                ) {
                    when (step) {
                        LogStep.MENU -> LogMenu(
                            hasFloatingEntries = floatingHolidayEntries(timeAdjustments, bookedPtoDays).isNotEmpty(),
                            callInsRemaining = callInsRemainingForYear(appDate.year),
                            callInYear = appDate.year,
                            onSelect = { step = it },
                        )
                        LogStep.ATTENDANCE -> QuickAttendanceForm(appDate) { date, type, points, status, note ->
                            onAttendance(date, type, points, status, note)
                        }
                        LogStep.TIME -> QuickTimeForm(appDate, shiftPreset, birthdayMonth, timeAdjustments, bookedPtoDays, floatingAllowance) { date, kind, minutes, note, bookingId ->
                            onTimeUsed(date, kind, minutes, note, bookingId)
                        }
                        LogStep.CALL_IN -> QuickCallInForm(appDate, shiftPreset, callInsRemainingForYear, bookedPtoDays) { date, ptoMinutes, bookingId ->
                            onCallIn(date, ptoMinutes, bookingId)
                        }
                        LogStep.BOOKED_PTO -> QuickBookedPtoForm(appDate, birthdayMonth, bookedPtoDays, timeAdjustments, shiftPreset, floatingAllowance) { date, type, minutes, status ->
                            onBookPto(date, type, minutes, status)
                        }
                        LogStep.MANAGE_FLOATING -> ManageFloatingHolidaysForm(
                            adjustments = timeAdjustments,
                            bookedDays = bookedPtoDays,
                            onDeleteTimeAdjustment = onDeleteTimeAdjustment,
                            onDeleteBookedPto = onDeleteBookedPto,
                        )
                        LogStep.NOTE -> QuickNoteForm(appDate) { note -> onNote(appDate, note) }
                        LogStep.DOCUMENT -> QuickDocumentForm(
                            category = documentCategory,
                            onCategory = { documentCategory = it },
                            onCamera = { camera.launch(cameraUri) },
                            onChoose = { picker.launch(arrayOf("image/*", "application/pdf")) },
                        )
                        LogStep.WEEK_DONE -> WeekDoneForm(appDate) {
                            onNote(appDate, "Weekly review complete — no additional items to log.")
                        }
                    }
                }
            }
        }
    }
}

private fun logStepTitle(step: LogStep): String = when (step) {
    LogStep.MENU -> "+ LOG"
    LogStep.ATTENDANCE -> "Attendance issue"
    LogStep.CALL_IN -> "Call-in day"
    LogStep.TIME -> "PTO / sick time"
    LogStep.BOOKED_PTO -> "Booked PTO"
    LogStep.MANAGE_FLOATING -> "Floating holidays"
    LogStep.NOTE -> "Work note"
    LogStep.DOCUMENT -> "Add evidence"
    LogStep.WEEK_DONE -> "Weekly review"
}

@Composable
private fun LogMenu(
    hasFloatingEntries: Boolean,
    callInsRemaining: Int,
    callInYear: Int,
    onSelect: (LogStep) -> Unit,
) {
    val design = HubThemeDesign.tokens
    Column(verticalArrangement = Arrangement.spacedBy(design.contentSpacing)) {
        SectionLabel("What happened?", color = MaterialTheme.colorScheme.primary)
        LogMenuButton("●", design.pto, "Attendance issue", "Tardy, absence, left early, etc.") { onSelect(LogStep.ATTENDANCE) }
        LogMenuButton("◷", design.good, "PTO / sick time used", "Record time off used") { onSelect(LogStep.TIME) }
        LogMenuButton("▤", design.attention, "Work note", "Add a personal work note") { onSelect(LogStep.NOTE) }
        LogMenuButton("▱", design.document, "Scan / import document", "Add a photo or document") { onSelect(LogStep.DOCUMENT) }
        LogMenuButton("✓", design.good, "My week is up to date", "Mark weekly review complete") { onSelect(LogStep.WEEK_DONE) }
        SectionLabel("More log options")
        LogMenuButton(
            icon = "☎",
            color = design.attention,
            title = "Call-in day",
            detail = if (callInsRemaining > 0) {
                "Use one excused call-in, add 0 attendance points, and use one PTO day"
            } else {
                "No call-ins left for $callInYear • adds 0 attendance points"
            },
            enabled = callInsRemaining > 0,
        ) { onSelect(LogStep.CALL_IN) }
        LogMenuButton("＋", design.pto, "Book future PTO", "Remember a future vacation day") { onSelect(LogStep.BOOKED_PTO) }
        LogMenuButton(
            "−",
            design.pto,
            "Manage floating holidays",
            if (hasFloatingEntries) "View or remove a booked or used floater" else "No booked or used floaters to remove",
        ) { onSelect(LogStep.MANAGE_FLOATING) }
    }
}

private sealed interface FloatingHolidayEntry {
    val date: LocalDate
    val type: BookedTimeType

    data class Used(val adjustment: TimeBalanceAdjustment, override val type: BookedTimeType) : FloatingHolidayEntry {
        override val date: LocalDate = adjustment.occurredOn
    }

    data class Booked(val day: BookedPtoDay) : FloatingHolidayEntry {
        override val date: LocalDate = day.date
        override val type: BookedTimeType = day.type
    }
}

private fun floatingHolidayEntries(
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
): List<FloatingHolidayEntry> = buildList {
    adjustments.filter { it.minutes < 0 }.forEach { adjustment ->
        val type = when (adjustment.kind) {
            TimeBalanceKind.FLOATING_BIRTHDAY -> BookedTimeType.BIRTHDAY_FLOATING
            TimeBalanceKind.FLOATING_ANYTIME -> BookedTimeType.ANYTIME_FLOATING
            else -> null
        }
        if (type != null) add(FloatingHolidayEntry.Used(adjustment, type))
    }
    bookedDays.filter { it.type != BookedTimeType.REGULAR_PTO }.forEach { add(FloatingHolidayEntry.Booked(it)) }
}.sortedByDescending { it.date }

@Composable
private fun ManageFloatingHolidaysForm(
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
    onDeleteTimeAdjustment: (TimeBalanceAdjustment) -> Unit,
    onDeleteBookedPto: (BookedPtoDay) -> Unit,
) {
    val entries = floatingHolidayEntries(adjustments, bookedDays)
    var deleting by remember { mutableStateOf<FloatingHolidayEntry?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Removing a floater restores that floating-holiday type for the same calendar year.")
        if (entries.isEmpty()) {
            Text("No booked or used floating holidays are saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { entry ->
            HubPanel(Modifier.fillMaxWidth()) {
                Text(
                    when (entry.type) {
                        BookedTimeType.BIRTHDAY_FLOATING -> "Birthday floating holiday"
                        BookedTimeType.ANYTIME_FLOATING -> "Anytime floating holiday"
                        BookedTimeType.REGULAR_PTO -> "Floating holiday"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${entry.date.monthDayYear()} • ${if (entry is FloatingHolidayEntry.Booked) "Booked" else "Used"}")
                OutlinedButton(onClick = { deleting = entry }) { Text("Remove") }
            }
        }
    }

    deleting?.let { entry ->
        val state = if (entry is FloatingHolidayEntry.Booked) "booking" else "usage"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove floating holiday?") },
            text = { Text("This removes the $state from ${entry.date.monthDayYear()} and makes that floater available again for ${entry.date.year}.") },
            confirmButton = {
                Button(onClick = {
                    when (entry) {
                        is FloatingHolidayEntry.Booked -> onDeleteBookedPto(entry.day)
                        is FloatingHolidayEntry.Used -> onDeleteTimeAdjustment(entry.adjustment)
                    }
                    deleting = null
                }) { Text("REMOVE") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuickBookedPtoForm(
    appDate: LocalDate,
    birthdayMonth: Int?,
    bookedDays: List<BookedPtoDay>,
    adjustments: List<TimeBalanceAdjustment>,
    shiftPreset: String?,
    floatingAllowance: Int,
    onSave: (LocalDate, BookedTimeType, Int, app.hubhelper.domain.BookingStatus) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var type by remember { mutableStateOf(BookedTimeType.REGULAR_PTO) }
    var hours by rememberSaveable { mutableStateOf(if (shiftPreset == "SECOND") "10" else "8") }
    var approved by rememberSaveable { mutableStateOf(true) }
    val minutes = if (type == BookedTimeType.REGULAR_PTO) runCatching { Math.toIntExact(app.hubhelper.domain.Minutes.fromHours(hours).value) }.getOrNull() else 480
    val alreadySaved = bookedDays.any { it.date == date && it.type == type }
    val birthdayDateValid = type != BookedTimeType.BIRTHDAY_FLOATING || birthdayMonth == date.monthValue
    val floatingAvailable = type == BookedTimeType.REGULAR_PTO || floatingHolidayAvailable(type, adjustments, bookedDays, date.year, floatingAllowance)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField("Vacation date", date, { it?.let { selected -> date = selected } })
        listOf(
            BookedTimeType.REGULAR_PTO to "Regular PTO",
            BookedTimeType.BIRTHDAY_FLOATING to "Birthday floating",
            BookedTimeType.ANYTIME_FLOATING to "Anytime floating",
        ).forEach { (option, label) ->
            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(label) })
        }
        if (type == BookedTimeType.REGULAR_PTO) OutlinedTextField(hours, { hours = it }, label = { Text("Booked hours (partial days supported)") }, isError = minutes == null || minutes !in 1..1440)
        Row { FilterChip(approved, { approved = true }, label = { Text("Approved") }); FilterChip(!approved, { approved = false }, label = { Text("Requested") }) }
        Text(if (type == BookedTimeType.REGULAR_PTO) {
            "Approved hours deduct automatically on the booked date. Cancel the booking from Calendar to restore the balance. Requested hours do not deduct."
        } else "This uses one 8-hour floating holiday and does not reduce regular PTO.")
        if (!birthdayDateValid) Text("Birthday floating must be dated within your birthday month.", color = MaterialTheme.colorScheme.error)
        if (!floatingAvailable) Text("That floating holiday is already used or booked for this year.", color = MaterialTheme.colorScheme.error)
        if (alreadySaved) StatusBadge("Already saved", MaterialTheme.colorScheme.primary)
        Button(onClick = { onSave(date, type, minutes!!, if (approved) app.hubhelper.domain.BookingStatus.APPROVED else app.hubhelper.domain.BookingStatus.REQUESTED) }, enabled = minutes != null && minutes in 1..1440 && !alreadySaved && birthdayDateValid && floatingAvailable, modifier = Modifier.fillMaxWidth()) {
            Text(if (alreadySaved) "DATE ALREADY BOOKED" else if (type == BookedTimeType.REGULAR_PTO) "SAVE BOOKED PTO" else "SAVE FLOATING HOLIDAY")
        }
    }
}

@Composable
private fun QuickCallInForm(
    appDate: LocalDate,
    shiftPreset: String?,
    remainingForYear: (Int) -> Int,
    bookings: List<BookedPtoDay>,
    onSave: (LocalDate, Int, String?) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var bookingId by rememberSaveable { mutableStateOf<String?>(null) }
    val remaining = remainingForYear(date.year)
    val ptoHours = if (shiftPreset == "SECOND") 10 else 8
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField("Call-in date", date, { it?.let { selected -> date = selected } })
        StatusBadge("$remaining of 5 left", if (remaining > 1) HubThemeDesign.tokens.good else MaterialTheme.colorScheme.error)
        bookings.filter { it.date == date && it.type == BookedTimeType.REGULAR_PTO }.forEach { booking ->
            FilterChip(bookingId == booking.stableId, { bookingId = if (bookingId == booking.stableId) null else booking.stableId }, label = { Text("Replace booked PTO deduction") })
        }
        Text("This records an excused call-in day and deducts $ptoHours PTO hours for your ${if (shiftPreset == "SECOND") "second" else "first"} shift.")
        Text("It does not add an attendance point.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onSave(date, ptoHours * 60, bookingId) },
            enabled = remaining > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (remaining > 0) "RECORD CALL-IN DAY" else "NO CALL-INS LEFT FOR ${date.year}") }
    }
}

@Composable
private fun LogMenuButton(
    icon: String,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    HubPanel(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.18f), modifier = Modifier.size(42.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(icon, color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAttendanceForm(
    appDate: LocalDate,
    onSave: (LocalDate, AttendanceEventType, HalfPoints, AttendanceEventStatus, String?) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var type by remember { mutableStateOf(AttendanceEventType.TARDY) }
    var pointText by rememberSaveable { mutableStateOf("0.5") }
    var status by remember { mutableStateOf(AttendanceEventStatus.CONFIRMED) }
    var note by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    val decimal = pointText.toBigDecimalOrNull()
    val doubled = decimal?.multiply(BigDecimal(2))
    val halfPoints = doubled?.toInt()
    val valid = halfPoints != null && halfPoints >= 0 && doubled.compareTo(halfPoints.toBigDecimal()) == 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DatePickerField("Date", date, { it?.let { selected -> date = selected } })
        SectionLabel("Issue type")
        AttendanceEventType.entries.filterNot { it == AttendanceEventType.ATTENDANCE_CREDIT }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = {
                            type = option
                            pointText = if (option == AttendanceEventType.UNEXCUSED_ABSENCE) "1" else "0.5"
                        },
                        label = { Text(shortType(option)) },
                    )
                }
            }
        }
        Text("$pointText point${if (pointText == "1") "" else "s"} • ${status.name.lowercase()}", color = MaterialTheme.colorScheme.primary)
        Text(
            if (status == AttendanceEventStatus.CONFIRMED) {
                "This dated point counts now and is scheduled to fall off on ${date.plusMonths(12).monthDayYear()}."
            } else {
                "This dated entry stays in your history, but only confirmed points affect the total and receive a falloff."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "More details") }
        if (details) {
            OutlinedTextField(
                value = pointText,
                onValueChange = { pointText = it },
                label = { Text("Custom points") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !valid,
                modifier = Modifier.fillMaxWidth(),
            )
            AttendanceEventStatus.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { option ->
                        FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option.name.lowercase()) })
                    }
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = { onSave(date, type, HalfPoints(halfPoints!!), status, note.ifBlank { null }) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("CONFIRM ${shortType(type).uppercase()}") }
    }
}

private fun shortType(type: AttendanceEventType): String = when (type) {
    AttendanceEventType.UNEXCUSED_ABSENCE -> "Absence"
    AttendanceEventType.TARDY -> "Tardy"
    AttendanceEventType.LEFT_EARLY -> "Left early"
    AttendanceEventType.CALL_IN_VIOLATION -> "Call-in"
    AttendanceEventType.ATTENDANCE_CREDIT -> "Credit"
}

@Composable
private fun QuickTimeForm(
    appDate: LocalDate,
    shiftPreset: String?,
    birthdayMonth: Int?,
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
    floatingAllowance: Int,
    onSave: (LocalDate, TimeBalanceKind, Int, String?, String?) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var kind by remember { mutableStateOf(TimeBalanceKind.PTO) }
    var ptoType by remember { mutableStateOf(BookedTimeType.REGULAR_PTO) }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf(false) }
    var bookingId by rememberSaveable { mutableStateOf<String?>(null) }
    val fullDay = if (shiftPreset == "SECOND") 10 else 8
    val floating = kind == TimeBalanceKind.PTO && ptoType != BookedTimeType.REGULAR_PTO
    val exactMinutes = if (floating) BigDecimal(480) else hoursText.toBigDecimalOrNull()?.multiply(BigDecimal(if (kind == TimeBalanceKind.SICK) 480 else 60))
    val minutes = exactMinutes?.toInt()
    val birthdayDateValid = ptoType != BookedTimeType.BIRTHDAY_FLOATING || birthdayMonth == date.monthValue
    val floatingAvailable = !floating || floatingHolidayAvailable(ptoType, adjustments, bookedDays, date.year, floatingAllowance)
    val valid = minutes != null && minutes > 0 && exactMinutes.compareTo(minutes.toBigDecimal()) == 0 && birthdayDateValid && floatingAvailable

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DatePickerField("Date used", date, { it?.let { selected -> date = selected; bookingId = null } })
        if (kind == TimeBalanceKind.PTO && ptoType == BookedTimeType.REGULAR_PTO) {
            Text("Link this usage to a booking to avoid a second deduction:")
            FilterChip(bookingId == null, { bookingId = null }, label = { Text("Separate usage") })
            bookedDays.filter { it.date == date && it.type == BookedTimeType.REGULAR_PTO && adjustments.none { usage -> usage.bookingId == it.stableId } }.forEach { booking ->
                FilterChip(bookingId == booking.stableId, { bookingId = booking.stableId }, label = { Text("${app.hubhelper.domain.displayMinutesAsHours(booking.durationMinutes)} h booked") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TimeBalanceKind.PTO, TimeBalanceKind.SICK).forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option; hoursText = "" }, label = { Text(option.name) })
            }
        }
        if (kind == TimeBalanceKind.PTO) {
            listOf(
                BookedTimeType.REGULAR_PTO to "Regular",
                BookedTimeType.BIRTHDAY_FLOATING to "Birthday floating",
                BookedTimeType.ANYTIME_FLOATING to "Anytime floating",
            ).forEach { (option, label) ->
                FilterChip(selected = ptoType == option, onClick = { ptoType = option; hoursText = "" }, label = { Text(label) })
            }
        }
        if (!floating) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { hoursText = (if (kind == TimeBalanceKind.SICK) 1 else fullDay).toString() }) { Text(if (kind == TimeBalanceKind.SICK) "1 sick day" else "Full day") }
            if (kind == TimeBalanceKind.PTO) {
                OutlinedButton(onClick = { hoursText = (fullDay / 2).toString() }) { Text("Half day") }
            }
        }
        if (floating) {
            Text("One floating holiday • 8 hours")
            if (!birthdayDateValid) Text("Birthday floating must be used during your birthday month.", color = MaterialTheme.colorScheme.error)
            if (!floatingAvailable) Text("That floating holiday is already used or booked for this year.", color = MaterialTheme.colorScheme.error)
        } else OutlinedTextField(
            value = hoursText,
            onValueChange = { hoursText = it },
            label = { Text(if (kind == TimeBalanceKind.SICK) "Sick days used" else "Hours used") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = hoursText.isNotBlank() && !valid,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "More details") }
        if (details) OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val savedKind = if (kind == TimeBalanceKind.SICK) TimeBalanceKind.SICK else when (ptoType) {
                    BookedTimeType.BIRTHDAY_FLOATING -> TimeBalanceKind.FLOATING_BIRTHDAY
                    BookedTimeType.ANYTIME_FLOATING -> TimeBalanceKind.FLOATING_ANYTIME
                    BookedTimeType.REGULAR_PTO -> kind
                }
                onSave(date, savedKind, -minutes!!, note.ifBlank { null }, if (savedKind == TimeBalanceKind.PTO && !floating) bookingId else null)
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("RECORD TIME USED") }
    }
}

@Composable
private fun QuickNoteForm(appDate: LocalDate, onSave: (String) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Date • ${appDate.monthDayYear()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("What should you remember?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(note) }, enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("SAVE NOTE") }
    }
}

@Composable
private fun QuickDocumentForm(
    category: DocumentCategory,
    onCategory: (DocumentCategory) -> Unit,
    onCamera: () -> Unit,
    onChoose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Choose the evidence type, then select an image or PDF from your phone.")
        listOf(DocumentCategory.ATTENDANCE, DocumentCategory.HOLIDAY_CALENDAR, DocumentCategory.EXCEPTION_FORM, DocumentCategory.PAY, DocumentCategory.OTHER).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(selected = category == option, onClick = { onCategory(option) }, label = { Text(documentLabel(option)) })
                }
            }
        }
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("TAKE PHOTO") }
        OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text("CHOOSE FILE") }
        Text("Images and PDF pages are read on-device. Originals are preserved.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun documentLabel(category: DocumentCategory): String = when (category) {
    DocumentCategory.ATTENDANCE -> "Attendance"
    DocumentCategory.HOLIDAY_CALENDAR -> "Holiday"
    DocumentCategory.EXCEPTION_FORM -> "Exception"
    DocumentCategory.PTO -> "PTO"
    DocumentCategory.PAY -> "Pay"
    DocumentCategory.BENEFITS -> "Benefits"
    DocumentCategory.POLICY -> "Policy"
    DocumentCategory.CONTRACT -> "Contract"
    DocumentCategory.OTHER -> "Other"
}

@Composable
private fun WeekDoneForm(appDate: LocalDate, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProvenanceBadge("User")
        Text("Record that you reviewed your workweek on ${appDate.monthDayYear()} and have nothing else to add.")
        Text("A guided checklist can be added here later without changing the logging model.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("MY WEEK IS UP TO DATE") }
    }
}
