package app.hubhelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.DayOfWeek
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.net.Uri

@Composable
internal fun SettingsScreen(
    padding: PaddingValues,
    appDate: LocalDate,
    overrideDate: LocalDate?,
    onDateOverrideChanged: (LocalDate?) -> Unit,
    onEditSetup: () -> Unit,
    reminderPreference: ReminderPreference,
    onReminderChanged: (ReminderPreference) -> Unit,
    appLockEnabled: Boolean,
    onAppLockChanged: (Boolean) -> Unit,
    selectedTheme: HubTheme,
    onThemeChanged: (HubTheme) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onExport: (Uri) -> Unit,
    onImportBackup: (Uri, Boolean) -> Unit,
    onResetApp: () -> Unit,
    onOpenManual: () -> Unit,
    onAddBalanceCorrection: (app.hubhelper.domain.TimeBalanceKind, Int, String?) -> Unit,
    onSetAttendancePoints: (String) -> Unit,
    onSetCallIns: (Int) -> Unit,
    attendanceEvents: List<app.hubhelper.domain.AttendanceEvent>,
    onAddPastPoint: (LocalDate, app.hubhelper.domain.AttendanceEventType, app.hubhelper.domain.HalfPoints, app.hubhelper.domain.AttendanceEventStatus, String?) -> Unit,
    onUpdatePastPoint: (app.hubhelper.domain.AttendanceEvent, app.hubhelper.domain.AttendanceEvent) -> Unit,
    onDeletePastPoint: (app.hubhelper.domain.AttendanceEvent) -> Unit,
) {
    val context = LocalContext.current
    var reminderNotice by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            reminderNotice = "Notifications are allowed. Your reminder is scheduled."
            onReminderChanged(reminderPreference.copy(enabled = true))
        } else {
            reminderNotice = "Notifications are blocked. Allow notifications in Android Settings to receive reminders."
            onReminderChanged(reminderPreference.copy(enabled = false))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onExport)
    }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    var showReminderTimePicker by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(HubThemeDesign.tokens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(HubThemeDesign.tokens.contentSpacing),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        SectionLabel("Theme")
        HubTheme.entries.forEach { theme ->
            FilterChip(
                selected = selectedTheme == theme,
                onClick = { onThemeChanged(theme) },
                label = { Text(theme.displayName) },
            )
        }
        SectionLabel("Mode")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChanged(mode) },
                    label = { Text(mode.displayName) },
                )
            }
        }
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text("Cloud backup and network access are disabled.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = appLockEnabled, onCheckedChange = onAppLockChanged)
            Text(if (appLockEnabled) "App lock enabled" else "App lock disabled")
        }
        OutlinedButton(onClick = onEditSetup, modifier = Modifier.fillMaxWidth()) {
            Text("Edit hire date, shift, balances, and payday")
        }
        BalanceCorrectionTools(onAddBalanceCorrection, onSetAttendancePoints, onSetCallIns)
        PastPointsTools(appDate, attendanceEvents, onAddPastPoint, onUpdatePastPoint, onDeletePastPoint)
        OutlinedButton(onClick = onOpenManual, modifier = Modifier.fillMaxWidth()) {
            Text("Open user manual")
        }
        ShareHubHelperPanel()
        OutlinedButton(
            onClick = { exportLauncher.launch("hub-helper-backup.zip") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export private backup") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import backup") }
        val lastBackup = context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).getLong("last_success", 0)
        Text(if (lastBackup == 0L) "No successful export recorded on this device" else "Last successful export: ${java.time.Instant.ofEpochMilli(lastBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}", style = MaterialTheme.typography.bodySmall)
        Text("Exports contain personal records and original documents. Store them securely.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { showResetConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset app to zero") }
        Text("Weekly check-in", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = reminderPreference.enabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        reminderNotice = "Android will ask for notification permission."
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onReminderChanged(reminderPreference.copy(enabled = enabled))
                    }
                },
            )
            Text(if (reminderPreference.enabled) "Reminder enabled" else "Reminder disabled")
        }
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                WeeklyReminderScheduler.notifyNow(context)
                reminderNotice = "Test notification sent."
            } else {
                reminderNotice = "Allow notifications first, then try the test again."
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Send test reminder") }
        reminderNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        DayOfWeek.entries.chunked(4).forEach { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { day ->
                    FilterChip(
                        selected = reminderPreference.dayOfWeek == day,
                        onClick = { onReminderChanged(reminderPreference.copy(dayOfWeek = day)) },
                        label = { Text(day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        Button(
            onClick = { showReminderTimePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("TIME SET • ${reminderPreference.time.format(DateTimeFormatter.ofPattern("h:mm a"))}") }
        Text("Uses the phone's local time. Android may delay background work slightly to protect battery.", style = MaterialTheme.typography.bodySmall)
        DebugTools(appDate, overrideDate, onDateOverrideChanged)
        Text("Hub Helper ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall)
    }
    if (showReminderTimePicker) {
        ReminderTimePickerDialog(
            initialTime = reminderPreference.time,
            onDismiss = { showReminderTimePicker = false },
            onSet = { time ->
                onReminderChanged(reminderPreference.copy(time = time))
                showReminderTimePicker = false
            },
        )
    }
    pendingImport?.let { uri ->
        var previewError by remember(uri) { mutableStateOf(false) }
        val preview by androidx.compose.runtime.produceState<String?>(null, uri) {
            value = runCatching { BackupPreview.read(context, uri) }.getOrElse { previewError = true; it.message ?: "Cannot read backup" }
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("IMPORT BACKUP?") },
            text = { Text("${preview ?: "Reading backup…"}\n\nMerge keeps existing records and skips identical records in current backups; conflicts stop the import. Replace restores the backup snapshot and removes current records. Legacy backups support Merge only. Validation completes before records change.") },
            confirmButton = {
                Column {
                    Button(enabled = preview != null && !previewError, onClick = { onImportBackup(uri, false); pendingImport = null }) { Text("MERGE") }
                    TextButton(enabled = preview?.startsWith("Format 7") == true && !previewError, onClick = { onImportBackup(uri, true); pendingImport = null }) { Text("REPLACE ALL WITH BACKUP") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("DELETE ALL APP DATA?") },
            text = { Text("This permanently deletes all attendance history, PTO and sick entries, call-ins, booked dates, holidays, notes, saved documents, and setup values. The app will start first-time setup again. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onResetApp()
                    showResetConfirmation = false
                }) { Text("DELETE AND START OVER") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ShareHubHelperPanel() {
    val context = LocalContext.current
    HubPanel(Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
        SectionLabel("Share Hub Helper")
        Text("Help a coworker find the official Hub Helper download page.", style = MaterialTheme.typography.bodyMedium)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(role = Role.Button) { openHubHelperLandingPage(context) }
                .semantics { contentDescription = "Open the Hub Helper download page" },
            color = Color.White,
            shape = MaterialTheme.shapes.small,
        ) {
            Image(
                painter = painterResource(R.drawable.hub_helper_download_qr),
                contentDescription = null,
                modifier = Modifier.size(232.dp).padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            "Scan the code with a phone camera, or use one of the options below.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { openHubHelperLandingPage(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("OPEN DOWNLOAD PAGE") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { copyHubHelperLink(context) },
                modifier = Modifier.weight(1f),
            ) { Text("COPY LINK") }
            OutlinedButton(
                onClick = { shareHubHelperLink(context) },
                modifier = Modifier.weight(1f),
            ) { Text("SHARE LINK") }
        }
    }
}

internal fun openHubHelperLandingPage(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HUB_HELPER_LANDING_PAGE)))
    }.onFailure {
        Toast.makeText(context, "No browser is available on this phone.", Toast.LENGTH_LONG).show()
    }
}

internal fun copyHubHelperLink(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Hub Helper download page", HUB_HELPER_LANDING_PAGE))
    Toast.makeText(context, "Hub Helper link copied.", Toast.LENGTH_SHORT).show()
}

internal fun shareHubHelperLink(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Hub Helper")
        putExtra(Intent.EXTRA_TEXT, "Download Hub Helper: $HUB_HELPER_LANDING_PAGE")
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Share Hub Helper"))
    }.onFailure {
        Toast.makeText(context, "No sharing app is available on this phone.", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimePickerDialog(initialTime: LocalTime, onDismiss: () -> Unit, onSet: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET REMINDER TIME") },
        text = { TimePicker(state) },
        confirmButton = {
            Button(onClick = { onSet(LocalTime.of(state.hour, state.minute)) }) { Text("SET TIME") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun PastPointsTools(
    appDate: LocalDate,
    events: List<app.hubhelper.domain.AttendanceEvent>,
    onAdd: (LocalDate, app.hubhelper.domain.AttendanceEventType, app.hubhelper.domain.HalfPoints, app.hubhelper.domain.AttendanceEventStatus, String?) -> Unit,
    onUpdate: (app.hubhelper.domain.AttendanceEvent, app.hubhelper.domain.AttendanceEvent) -> Unit,
    onDelete: (app.hubhelper.domain.AttendanceEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<app.hubhelper.domain.AttendanceEvent?>(null) }
    var deleting by remember { mutableStateOf<app.hubhelper.domain.AttendanceEvent?>(null) }

    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Close past-points editor" else "Add, edit, or remove past points")
    }
    if (expanded) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Past points and falloffs", style = MaterialTheme.typography.titleMedium)
                Text("Adding historical evidence preserves the reported balance and schedules falloffs. Correcting or rescinding an event recalculates its contribution.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { adding = !adding; editing = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (adding) "Close point form" else "Add past point")
                }
                if (adding) {
                    AttendanceEventForm(appDate, null) { date, type, points, status, note ->
                        onAdd(date, type, points, status, note)
                        adding = false
                    }
                }
                editing?.let { existing ->
                    AttendanceEventForm(existing.occurredOn, existing) { date, type, points, status, note ->
                        onUpdate(existing, existing.copy(occurredOn = date, type = type, points = points, status = status, note = note))
                        editing = null
                    }
                }
                if (events.isEmpty()) Text("No dated points saved.")
                events.sortedByDescending { it.occurredOn }.forEach { event ->
                    HubPanel(Modifier.fillMaxWidth()) {
                        Text("${event.occurredOn.monthDayYear()} • ${event.points.asDisplayValue()} points", fontWeight = FontWeight.SemiBold)
                        Text(event.type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase))
                        Text("Falloff: ${if (event.type == app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT) "No annual falloff" else event.occurredOn.plusMonths(12).monthDayYear()}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editing = event; adding = false }) { Text("Edit") }
                            OutlinedButton(onClick = { deleting = event }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("REMOVE PAST POINT?") },
            text = { Text("This removes the ${event.occurredOn.monthDayYear()} entry and its scheduled falloff.") },
            confirmButton = {
                Button(onClick = { onDelete(event); deleting = null }) { Text("REMOVE") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun BalanceCorrectionTools(
    onSave: (app.hubhelper.domain.TimeBalanceKind, Int, String?) -> Unit,
    onSetAttendancePoints: (String) -> Unit,
    onSetCallIns: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var kind by remember { mutableStateOf(app.hubhelper.domain.TimeBalanceKind.PTO) }
    var addHours by rememberSaveable { mutableStateOf(true) }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("PTO") }
    val decimalHours = hoursText.toBigDecimalOrNull()
    val exactMinutes = decimalHours?.multiply(BigDecimal(if (target == "SICK") 480 else 60))
    val minutes = exactMinutes?.toInt()
    val timeValid = minutes != null && minutes > 0 && exactMinutes.compareTo(BigDecimal(minutes)) == 0
    val pointsValid = decimalHours?.let { it >= BigDecimal(-1) && it.remainder(BigDecimal("0.5")).signum() == 0 } == true
    val callInsValid = hoursText.toIntOrNull() in 0..5
    val valid = when (target) { "POINTS" -> pointsValid; "CALL_INS" -> callInsValid; else -> timeValid }

    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Close balance correction" else "Correct balances, points, or call-ins")
    }
    if (expanded) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("PTO", "SICK", "POINTS", "CALL_INS").chunked(2).forEach { options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            FilterChip(selected = target == option, onClick = {
                                target = option
                                if (option == "PTO" || option == "SICK") kind = app.hubhelper.domain.TimeBalanceKind.valueOf(option)
                                hoursText = ""
                            }, label = { Text(option.replace('_', ' ')) })
                        }
                    }
                }
                if (target == "PTO" || target == "SICK") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = addHours, onClick = { addHours = true }, label = { Text("Add") })
                        FilterChip(selected = !addHours, onClick = { addHours = false }, label = { Text("Remove") })
                    }
                }
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text(when (target) {
                        "SICK" -> "Positive number of sick days"
                        "POINTS" -> "Set current attendance points"
                        "CALL_INS" -> "Set call-in days remaining"
                        else -> "Positive number of hours"
                    }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = hoursText.isNotBlank() && !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (target == "PTO" || target == "SICK") {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Reason for correction") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        when (target) {
                            "POINTS" -> onSetAttendancePoints(hoursText)
                            "CALL_INS" -> onSetCallIns(hoursText.toInt())
                            else -> onSave(kind, if (addHours) minutes!! else -minutes!!, note.ifBlank { "Manual correction" })
                        }
                        hoursText = ""
                        note = ""
                        expanded = false
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save correction") }
            }
        }
    }
}
