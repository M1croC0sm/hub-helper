package app.hubhelper

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

private enum class LogStep { MENU, ATTENDANCE, CALL_IN, TIME, BOOKED_PTO, NOTE, DOCUMENT, WEEK_DONE }

@Composable
fun GlobalLogDialog(
    appDate: LocalDate,
    shiftPreset: String?,
    onDismiss: () -> Unit,
    onAttendance: (LocalDate, AttendanceEventType, HalfPoints, AttendanceEventStatus, String?) -> Unit,
    onTimeUsed: (LocalDate, TimeBalanceKind, Int, String?) -> Unit,
    callIns: List<CallInEvent>,
    callInsRemainingForYear: (Int) -> Int,
    onCallIn: (LocalDate, Int) -> Unit,
    bookedPtoDays: List<BookedPtoDay>,
    timeAdjustments: List<TimeBalanceAdjustment>,
    birthdayMonth: Int?,
    onBookPto: (LocalDate, BookedTimeType) -> Unit,
    onNote: (LocalDate, String) -> Unit,
    onDocument: (Uri, DocumentCategory) -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(LogStep.MENU) }
    var documentCategory by remember { mutableStateOf(DocumentCategory.ATTENDANCE) }
    val cameraFile = remember {
        File(context.cacheDir, "camera-captures/quick-log-${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
    }
    val cameraUri = remember(cameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onDocument(it, documentCategory); onDismiss() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            onDocument(cameraUri, documentCategory)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                SectionLabel("Quick input", color = MaterialTheme.colorScheme.primary)
                Text(if (step == LogStep.MENU) "+ LOG" else logStepTitle(step), style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            when (step) {
                LogStep.MENU -> LogMenu { step = it }
                LogStep.ATTENDANCE -> QuickAttendanceForm(appDate) { date, type, points, status, note ->
                    onAttendance(date, type, points, status, note)
                    onDismiss()
                }
                LogStep.TIME -> QuickTimeForm(appDate, shiftPreset, birthdayMonth, timeAdjustments, bookedPtoDays) { date, kind, minutes, note ->
                    onTimeUsed(date, kind, minutes, note)
                    onDismiss()
                }
                LogStep.CALL_IN -> QuickCallInForm(appDate, shiftPreset, callInsRemainingForYear) { date, ptoMinutes ->
                    onCallIn(date, ptoMinutes)
                    onDismiss()
                }
                LogStep.BOOKED_PTO -> QuickBookedPtoForm(appDate, birthdayMonth, bookedPtoDays, timeAdjustments) { date, type ->
                    onBookPto(date, type)
                    onDismiss()
                }
                LogStep.NOTE -> QuickNoteForm(appDate) { note -> onNote(appDate, note); onDismiss() }
                LogStep.DOCUMENT -> QuickDocumentForm(
                    category = documentCategory,
                    onCategory = { documentCategory = it },
                    onCamera = { camera.launch(cameraUri) },
                    onChoose = { picker.launch(arrayOf("image/*", "application/pdf")) },
                )
                LogStep.WEEK_DONE -> WeekDoneForm(appDate) {
                    onNote(appDate, "Weekly review complete — no additional items to log.")
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (step == LogStep.MENU) onDismiss() else step = LogStep.MENU }) {
                Text(if (step == LogStep.MENU) "Close" else "Back")
            }
        },
    )
}

private fun logStepTitle(step: LogStep): String = when (step) {
    LogStep.MENU -> "+ LOG"
    LogStep.ATTENDANCE -> "Attendance issue"
    LogStep.CALL_IN -> "Call-in day"
    LogStep.TIME -> "PTO / sick time"
    LogStep.BOOKED_PTO -> "Booked PTO"
    LogStep.NOTE -> "Work note"
    LogStep.DOCUMENT -> "Add evidence"
    LogStep.WEEK_DONE -> "Weekly review"
}

@Composable
private fun LogMenu(onSelect: (LogStep) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("What happened?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LogMenuButton("Attendance issue", "Absence, tardy, left early, or call-in violation") { onSelect(LogStep.ATTENDANCE) }
        LogMenuButton("Call-in day", "Use one of five excused days and one PTO day") { onSelect(LogStep.CALL_IN) }
        LogMenuButton("PTO / sick time used", "Record time against a balance") { onSelect(LogStep.TIME) }
        LogMenuButton("Booked PTO", "Remember a future vacation day") { onSelect(LogStep.BOOKED_PTO) }
        LogMenuButton("Work note", "Save a dated personal note") { onSelect(LogStep.NOTE) }
        LogMenuButton("Scan / import document", "Attach evidence from your phone") { onSelect(LogStep.DOCUMENT) }
        LogMenuButton("My week is up to date", "Record that you completed your review") { onSelect(LogStep.WEEK_DONE) }
    }
}

@Composable
private fun QuickBookedPtoForm(
    appDate: LocalDate,
    birthdayMonth: Int?,
    bookedDays: List<BookedPtoDay>,
    adjustments: List<TimeBalanceAdjustment>,
    onSave: (LocalDate, BookedTimeType) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var type by remember { mutableStateOf(BookedTimeType.REGULAR_PTO) }
    val alreadySaved = bookedDays.any { it.date == date }
    val birthdayDateValid = type != BookedTimeType.BIRTHDAY_FLOATING || birthdayMonth == date.monthValue
    val floatingAvailable = type == BookedTimeType.REGULAR_PTO || floatingHolidayAvailable(type, adjustments, bookedDays, date.year)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField("Vacation date", date, { it?.let { selected -> date = selected } })
        listOf(
            BookedTimeType.REGULAR_PTO to "Regular PTO",
            BookedTimeType.BIRTHDAY_FLOATING to "Birthday floating",
            BookedTimeType.ANYTIME_FLOATING to "Anytime floating",
        ).forEach { (option, label) ->
            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(label) })
        }
        Text(if (type == BookedTimeType.REGULAR_PTO) {
            "PTO is deducted automatically when this date arrives: 8 hours on first shift or 10 hours on second shift."
        } else "This uses one 8-hour floating holiday and does not reduce regular PTO.")
        if (!birthdayDateValid) Text("Birthday floating must be dated within your birthday month.", color = MaterialTheme.colorScheme.error)
        if (!floatingAvailable) Text("That floating holiday is already used or booked for this year.", color = MaterialTheme.colorScheme.error)
        if (alreadySaved) StatusBadge("Already saved", MaterialTheme.colorScheme.primary)
        Button(onClick = { onSave(date, type) }, enabled = !alreadySaved && birthdayDateValid && floatingAvailable, modifier = Modifier.fillMaxWidth()) {
            Text(if (alreadySaved) "DATE ALREADY BOOKED" else if (type == BookedTimeType.REGULAR_PTO) "SAVE BOOKED PTO" else "SAVE FLOATING HOLIDAY")
        }
    }
}

@Composable
private fun QuickCallInForm(
    appDate: LocalDate,
    shiftPreset: String?,
    remainingForYear: (Int) -> Int,
    onSave: (LocalDate, Int) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    val remaining = remainingForYear(date.year)
    val ptoHours = if (shiftPreset == "SECOND") 10 else 8
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField("Call-in date", date, { it?.let { selected -> date = selected } })
        StatusBadge("$remaining of 5 left", if (remaining > 1) HubColors.Green else MaterialTheme.colorScheme.error)
        Text("This records an excused call-in day and deducts $ptoHours PTO hours for your ${if (shiftPreset == "SECOND") "second" else "first"} shift.")
        Text("It does not add an attendance point.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onSave(date, ptoHours * 60) },
            enabled = remaining > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (remaining > 0) "RECORD CALL-IN DAY" else "NO CALL-INS LEFT FOR ${date.year}") }
    }
}

@Composable
private fun LogMenuButton(title: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var pointText by remember { mutableStateOf("0.5") }
    var status by remember { mutableStateOf(AttendanceEventStatus.CONFIRMED) }
    var note by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
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
    onSave: (LocalDate, TimeBalanceKind, Int, String?) -> Unit,
) {
    var date by remember { mutableStateOf(appDate) }
    var kind by remember { mutableStateOf(TimeBalanceKind.PTO) }
    var ptoType by remember { mutableStateOf(BookedTimeType.REGULAR_PTO) }
    var hoursText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    val fullDay = if (shiftPreset == "SECOND") 10 else 8
    val floating = kind == TimeBalanceKind.PTO && ptoType != BookedTimeType.REGULAR_PTO
    val exactMinutes = if (floating) BigDecimal(480) else hoursText.toBigDecimalOrNull()?.multiply(BigDecimal(if (kind == TimeBalanceKind.SICK) 480 else 60))
    val minutes = exactMinutes?.toInt()
    val birthdayDateValid = ptoType != BookedTimeType.BIRTHDAY_FLOATING || birthdayMonth == date.monthValue
    val floatingAvailable = !floating || floatingHolidayAvailable(ptoType, adjustments, bookedDays, date.year)
    val valid = minutes != null && minutes > 0 && exactMinutes.compareTo(minutes.toBigDecimal()) == 0 && birthdayDateValid && floatingAvailable

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DatePickerField("Date used", date, { it?.let { selected -> date = selected } })
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
                onSave(date, savedKind, -minutes!!, note.ifBlank { null })
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("RECORD TIME USED") }
    }
}

@Composable
private fun QuickNoteForm(appDate: LocalDate, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
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
        DocumentCategory.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    FilterChip(selected = category == option, onClick = { onCategory(option) }, label = { Text(documentLabel(option)) })
                }
            }
        }
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("TAKE PHOTO") }
        OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) { Text("CHOOSE FILE") }
        Text("Images are read on-device. PDFs are preserved but PDF OCR is not yet supported.", style = MaterialTheme.typography.bodySmall)
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
