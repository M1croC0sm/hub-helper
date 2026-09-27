package app.hubhelper

import androidx.room.withTransaction
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.hubhelper.data.AttendanceRepository
import app.hubhelper.data.TimeBalanceRepository
import app.hubhelper.data.DocumentRepository
import app.hubhelper.data.WorkNoteRepository
import app.hubhelper.data.HolidayRepository
import app.hubhelper.data.CallInRepository
import app.hubhelper.data.BookedPtoRepository
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.TimeOffCalculator
import app.hubhelper.domain.ParsedAttendanceStatement
import app.hubhelper.domain.WorkDocument
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import java.time.temporal.ChronoUnit
import android.net.Uri

internal enum class MainArea(val label: String, val shortLabel: String) {
    HOME("Home", "Home"),
    CALENDAR("Calendar", "Calendar"),
    ATTENDANCE_DETAILS("Attendance details", "Details"),
    REFERENCE("Reference", "Reference"),
    DOCUMENTS("Documents", "Documents"),
    SETTINGS("Settings", "Settings"),
    MANUAL("User Manual", "Manual"),
}

internal const val HUB_HELPER_LANDING_PAGE = "https://m1croc0sm.github.io/hub-helper/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubHelperApp(
    operations: AppOperations,
    appDate: LocalDate,
    overrideDate: LocalDate?,
    onDateOverrideChanged: (LocalDate?) -> Unit,
    setupData: SetupData,
    onSetupDataChanged: (SetupData) -> Unit,
    attendanceRepository: AttendanceRepository,
    timeBalanceRepository: TimeBalanceRepository,
    documentRepository: DocumentRepository,
    workNoteRepository: WorkNoteRepository,
    holidayRepository: HolidayRepository,
    callInRepository: CallInRepository,
    bookedPtoRepository: BookedPtoRepository,
    documentOcr: DocumentOcr,
    onEditSetup: () -> Unit,
    onApplyAttendanceStatement: (WorkDocument, ParsedAttendanceStatement) -> Unit,
    reminderPreference: ReminderPreference,
    onReminderChanged: (ReminderPreference) -> Unit,
    appLockEnabled: Boolean,
    onAppLockChanged: (Boolean) -> Unit,
    selectedTheme: HubTheme,
    onThemeChanged: (HubTheme) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    darkMode: Boolean,
    onImportBackup: (Uri, Boolean) -> Unit,
    onResetApp: () -> Unit,
    lastAcknowledgedYear: Int,
    onYearAcknowledged: (Int) -> Unit,
) {
    var selectedArea by rememberSaveable { mutableStateOf(MainArea.HOME) }
    var calendarRequest by remember { mutableStateOf(CalendarRequest()) }
    var calendarRequestVersion by remember { mutableIntStateOf(0) }
    var referenceRootVersion by remember { mutableIntStateOf(0) }
    var showGlobalLog by rememberSaveable { mutableStateOf(false) }
    var logDate by remember { mutableStateOf<LocalDate?>(null) }
    val appContext = LocalContext.current.applicationContext
    BackHandler(enabled = selectedArea != MainArea.HOME) { selectedArea = MainArea.HOME }
    val activity = requireNotNull(androidx.activity.compose.LocalActivity.current) as androidx.fragment.app.FragmentActivity
    val ledger = androidx.lifecycle.ViewModelProvider(activity)[LedgerViewModel::class.java]
    val state by ledger.state.collectAsStateWithLifecycle()
    if (!state.ready) {
        Text(state.error ?: "Loading your records…", modifier = Modifier.padding(24.dp))
        return
    }
    val events = state.events
    val timeAdjustments = state.adjustments
    val documents = state.documents
    val workNotes = state.notes
    val holidays = state.holidays
    val callIns = state.callIns
    val bookedPtoDays = state.bookings.filter { it.status != app.hubhelper.domain.BookingStatus.CANCELLED }
    val coroutineScope = operations
    val paydays = remember(setupData.paydayAnchor, appDate.year) {
        runCatching { LocalDate.parse(setupData.paydayAnchor) }
            .map { app.hubhelper.domain.PaydayCalculator.everyOtherFriday(it, LocalDate.of(appDate.year, 12, 31)) }
            .getOrDefault(emptyList())
    }
    LaunchedEffect(setupData.shiftPreset, appDate.year) {
        val secondShift = setupData.shiftPreset == "SECOND"
        holidayRepository.ensureContractHolidays(appDate.year, secondShift)
        holidayRepository.ensureContractHolidays(appDate.year + 1, secondShift)
    }
    fun callInsRemainingFor(year: Int): Int {
        val saved = setupData.callInsRemaining.toIntOrNull()
        return if (setupData.callInsBalanceYear.toIntOrNull() == year && saved != null) {
            saved.coerceIn(0, app.hubhelper.domain.ANNUAL_CALL_IN_ALLOWANCE)
        } else {
            app.hubhelper.domain.remainingCallIns(callIns, year)
        }
    }
    fun saveCallInsRemaining(year: Int, remaining: Int) {
        onSetupDataChanged(
            setupData.copy(
                callInsRemaining = remaining.coerceIn(0, app.hubhelper.domain.ANNUAL_CALL_IN_ALLOWANCE).toString(),
                callInsBalanceYear = year.toString(),
            ),
        )
    }

    HubHelperTheme(selectedTheme, darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val title = when (selectedArea) {
                                MainArea.HOME -> "HUB HELPER"
                                else -> selectedArea.label
                            }
                            Text(title, style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            TextButton(
                                modifier = Modifier.semantics { contentDescription = if (selectedArea == MainArea.SETTINGS || selectedArea == MainArea.MANUAL) "Return to Home" else "Open Settings" },
                                onClick = {
                                selectedArea = when (selectedArea) {
                                    MainArea.SETTINGS, MainArea.MANUAL -> MainArea.HOME
                                    else -> MainArea.SETTINGS
                                }
                            }) {
                                Text(if (selectedArea == MainArea.SETTINGS || selectedArea == MainArea.MANUAL) "Done" else "⚙")
                            }
                        },
                    )
                },
                bottomBar = {
                    HubNavigationBar(
                        selectedArea = selectedArea,
                        onSelect = { area ->
                            if (area == MainArea.REFERENCE) referenceRootVersion++
                            if (area == MainArea.CALENDAR) {
                                calendarRequest = CalendarRequest()
                                calendarRequestVersion++
                            }
                            selectedArea = area
                        },
                        onLog = { logDate = null; showGlobalLog = true },
                    )
                },
            ) { padding ->
                when (selectedArea) {
                    MainArea.HOME -> HomeScreen(
                        padding,
                        appDate,
                        overrideDate != null,
                        setupData,
                        events,
                        timeAdjustments,
                        callIns,
                        callInsRemaining = callInsRemainingFor(appDate.year),
                        bookedPtoDays,
                        holidays = holidays,
                        paydays = paydays,
                        workNotes = workNotes,
                        onViewAttendanceDetails = { selectedArea = MainArea.ATTENDANCE_DETAILS },
                        onViewCalendar = { request ->
                            calendarRequest = request
                            calendarRequestVersion++
                            selectedArea = MainArea.CALENDAR
                        },
                    )
                    MainArea.CALENDAR -> key(calendarRequestVersion) { CalendarScreen(
                        padding = padding,
                        appDate = appDate,
                        openingBalance = setupData.attendanceOpeningRemainder,
                        events = events,
                        timeAdjustments = timeAdjustments,
                        callIns = callIns,
                        bookedPtoDays = bookedPtoDays,
                        holidays = holidays,
                        paydays = paydays,
                        request = calendarRequest,
                        onLogDate = { date -> logDate = date; showGlobalLog = true },
                        onUpdateAttendance = { updated -> coroutineScope.launch { attendanceRepository.update(updated) } },
                        onDeleteAttendance = { event -> coroutineScope.launch {
                            attendanceRepository.update(event.copy(status = app.hubhelper.domain.AttendanceEventStatus.RESCINDED))
                        } },
                        onDeleteTimeAdjustment = { adjustment ->
                            coroutineScope.launch { timeBalanceRepository.delete(adjustment) }
                        },
                        onDeleteCallIn = { event ->
                            coroutineScope.launch { callInRepository.delete(event) }
                        },
                        onDeleteBookedPto = { day -> coroutineScope.launch { bookedPtoRepository.delete(day) } },
                        onSetBookingStatus = { day, status -> coroutineScope.launch { bookedPtoRepository.setStatus(day, status) } },
                        onDeleteHoliday = { holiday -> coroutineScope.launch { holidayRepository.delete(holiday) } },
                    ) }
                    MainArea.ATTENDANCE_DETAILS -> AttendanceDetailsScreen(
                        padding = padding,
                        appDate = appDate,
                        openingBalance = setupData.attendanceOpeningRemainder,
                        balancesAsOfDate = setupData.attendanceAsOfDate.ifBlank { setupData.balancesAsOfDate },
                        events = events,
                        onViewRules = { selectedArea = MainArea.REFERENCE; referenceRootVersion++ },
                    )
                    MainArea.REFERENCE -> key(referenceRootVersion) { ContractLibraryScreen(padding, holidays, appDate) }
                    MainArea.DOCUMENTS -> DocumentLibraryScreen(
                        padding = padding,
                        documents = documents,
                        onImport = { uris, category ->
                            coroutineScope.launch {
                                val document = documentRepository.importPages(uris, category)
                                documentOcr.enqueue(document)
                            }
                        },
                        onDelete = { document -> coroutineScope.launch { documentRepository.delete(document) } },
                        onApplyAttendanceStatement = onApplyAttendanceStatement,
                        appDate = appDate,
                        notes = workNotes,
                        bookedPtoDays = bookedPtoDays,
                        onBookPto = { date, sourceId -> coroutineScope.launch { bookedPtoRepository.add(date, sourceId) } },
                        holidays = holidays,
                        onAddHoliday = { date, name -> coroutineScope.launch { holidayRepository.add(date, name) } },
                        onAddNote = { date, note -> coroutineScope.launch { workNoteRepository.add(date, note); showGlobalLog = false } },
                        onDeleteNote = { note -> coroutineScope.launch { workNoteRepository.delete(note) } },
                    )
                    MainArea.SETTINGS -> SettingsScreen(
                        padding,
                        appDate,
                        overrideDate,
                        onDateOverrideChanged,
                        onEditSetup,
                        reminderPreference,
                        onReminderChanged,
                        appLockEnabled,
                        onAppLockChanged,
                        selectedTheme,
                        onThemeChanged,
                        themeMode,
                        onThemeModeChanged,
                        onExport = { destination ->
                            coroutineScope.launch {
                                DatabaseBackup.export(appContext, destination)
                                Toast.makeText(appContext, "Backup verified and exported", Toast.LENGTH_LONG).show()
                            }
                        },
                        onImportBackup = onImportBackup,
                        onResetApp = onResetApp,
                        onOpenManual = { selectedArea = MainArea.MANUAL },
                        onAddBalanceCorrection = { kind, minutes, note ->
                            coroutineScope.launch { timeBalanceRepository.add(appDate, kind, minutes, note) }
                        },
                        onSetAttendancePoints = { value ->
                            val datedHalfPoints = AttendanceCalculator().summarize(events, appDate).confirmedPoints.value
                            val datedPoints = BigDecimal(datedHalfPoints).divide(BigDecimal(2))
                            val desired = value.toBigDecimal()
                            onSetupDataChanged(
                                setupData.copy(
                                    currentAttendancePoints = desired.stripTrailingZeros().toPlainString(),
                                    attendanceAsOfDate = appDate.toString(),
                                    attendanceOpeningRemainder = desired.subtract(datedPoints).stripTrailingZeros().toPlainString(),
                                ),
                            )
                        },
                        onSetCallIns = { remaining -> saveCallInsRemaining(appDate.year, remaining) },
                        attendanceEvents = events,
                        onAddPastPoint = { date, type, points, status, note ->
                            coroutineScope.launch {
                                val db = app.hubhelper.data.HubHelperDatabase.get(appContext)
                                db.withTransaction {
                                    attendanceRepository.add(date, type, points, status, note)
                                    val store = SetupStore(db)
                                    store.save((store.read() ?: setupData).withOpeningPointAdjustment(-attendanceContributionHalfPoints(date, type, points, status, appDate)), "historical evidence linked")
                                }
                            }
                        },
                        onUpdatePastPoint = { _, updated -> coroutineScope.launch { attendanceRepository.update(updated) } },
                        onDeletePastPoint = { event -> coroutineScope.launch { attendanceRepository.update(event.copy(status = app.hubhelper.domain.AttendanceEventStatus.RESCINDED)) } },
                    )
                    MainArea.MANUAL -> UserManualScreen(padding)
                }
            }
            if (showGlobalLog) {
                GlobalLogDialog(
                    appDate = logDate ?: appDate,
                    shiftPreset = setupData.shiftPreset,
                    onDismiss = { showGlobalLog = false; logDate = null },
                    onAttendance = { date, type, points, status, note ->
                        coroutineScope.launch { attendanceRepository.add(date, type, points, status, note); showGlobalLog = false }
                    },
                    onTimeUsed = { date, kind, minutes, note, bookingId ->
                        coroutineScope.launch { timeBalanceRepository.add(date, kind, minutes, note, bookingId); showGlobalLog = false }
                    },
                    callIns = callIns,
                    callInsRemainingForYear = ::callInsRemainingFor,
                    onCallIn = { date, ptoMinutes, bookingId ->
                        coroutineScope.launch { callInRepository.add(date, ptoMinutes, bookingId); showGlobalLog = false }
                    },
                    bookedPtoDays = bookedPtoDays,
                    timeAdjustments = timeAdjustments,
                    birthdayMonth = setupData.birthdayMonth.toIntOrNull(),
                    floatingAllowance = setupData.floatingHolidayAllowance.toIntOrNull() ?: 0,
                    onBookPto = { date, type, minutes, status -> coroutineScope.launch { bookedPtoRepository.add(date, type = type, durationMinutes = minutes, status = status); showGlobalLog = false } },
                    onDeleteTimeAdjustment = { adjustment ->
                        coroutineScope.launch { timeBalanceRepository.delete(adjustment) }
                    },
                    onDeleteBookedPto = { day -> coroutineScope.launch { bookedPtoRepository.delete(day) } },
                    onNote = { date, note -> coroutineScope.launch { workNoteRepository.add(date, note); showGlobalLog = false } },
                    onDocument = { uri, category ->
                        coroutineScope.launch {
                            val document = documentRepository.import(uri, category)
                            documentOcr.enqueue(document)
                        }
                    },
                )
            }
            if (appDate.year > lastAcknowledgedYear) {
                NewYearResetDialog(
                    year = appDate.year,
                    hireDate = setupData.hireDate,
                    onContinue = { onYearAcknowledged(appDate.year) },
                    onScanCalendar = {
                        onYearAcknowledged(appDate.year)
                        selectedArea = MainArea.DOCUMENTS
                    },
                )
            }
        }
    }
}

internal fun attendanceContributionHalfPoints(
    date: LocalDate,
    type: app.hubhelper.domain.AttendanceEventType,
    points: app.hubhelper.domain.HalfPoints,
    status: app.hubhelper.domain.AttendanceEventStatus,
    asOf: LocalDate,
): Int {
    if (status != app.hubhelper.domain.AttendanceEventStatus.CONFIRMED || date.isAfter(asOf)) return 0
    if (type != app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT && !asOf.isBefore(date.plusMonths(12))) return 0
    return if (type == app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT) -points.value else points.value
}

internal fun SetupData.withOpeningPointAdjustment(halfPointDelta: Int): SetupData {
    val current = attendanceOpeningRemainder.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val delta = BigDecimal(halfPointDelta).divide(BigDecimal(2))
    return copy(attendanceOpeningRemainder = current.add(delta).stripTrailingZeros().toPlainString())
}

@Composable
internal fun NewYearResetDialog(
    year: Int,
    hireDate: String,
    onContinue: () -> Unit,
    onScanCalendar: () -> Unit,
) {
    val ptoHours = runCatching {
        TimeOffCalculator.vacationHoursForYear(LocalDate.parse(hireDate), year)
    }.getOrNull()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("$year RESET COMPLETE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge("New year", HubThemeDesign.tokens.good)
                Text("Your annual counters now use the new calendar year.")
                HubPanel(Modifier.fillMaxWidth(), accent = HubThemeDesign.tokens.pto) {
                    SectionLabel("PTO allowance")
                    Text(ptoHours?.let { "$it hours for $year" } ?: "Add your hire date to calculate the full allowance")
                }
                HubPanel(Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
                    SectionLabel("Call-ins")
                    Text("5 available for $year")
                }
                Text("Contract holidays were loaded automatically. You can scan a calendar if your company adds a special or changed holiday.")
            }
        },
        confirmButton = { Button(onClick = onScanCalendar) { Text("SCAN ADDITIONS") } },
        dismissButton = { TextButton(onClick = onContinue) { Text("Continue") } },
    )
}

@Composable
internal fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

internal fun dateWithCountdown(date: LocalDate, today: LocalDate, canBeOverdue: Boolean): String {
    val days = ChronoUnit.DAYS.between(today, date)
    val countdown = when {
        days > 1 -> "$days days"
        days == 1L -> "Tomorrow"
        days == 0L -> "Today"
        canBeOverdue -> "May be due • ${-days} days ago"
        else -> "Passed"
    }
    return "${date.monthDayYear()} • $countdown"
}

@Composable
internal fun EmptyArea(padding: PaddingValues, title: String, explanation: String) {
    Column(modifier = Modifier.padding(padding).padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(explanation)
    }
}
