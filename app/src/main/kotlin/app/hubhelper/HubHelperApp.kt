package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import android.net.Uri

private enum class MainArea(val label: String, val shortLabel: String) {
    HOME("Home", "Home"),
    CALENDAR("Calendar", "Calendar"),
    ATTENDANCE_DETAILS("Attendance details", "Details"),
    REFERENCE("Reference", "Reference"),
    DOCUMENTS("Documents", "Documents"),
    SETTINGS("Settings", "Settings"),
    MANUAL("User Manual", "Manual"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubHelperApp(
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
    onImportBackup: (Uri) -> Unit,
    onResetApp: () -> Unit,
    lastAcknowledgedYear: Int,
    onYearAcknowledged: (Int) -> Unit,
) {
    var selectedArea by remember { mutableStateOf(MainArea.HOME) }
    var calendarRequest by remember { mutableStateOf(CalendarRequest()) }
    var calendarRequestVersion by remember { mutableIntStateOf(0) }
    var referenceRootVersion by remember { mutableIntStateOf(0) }
    var showGlobalLog by remember { mutableStateOf(false) }
    var logDate by remember { mutableStateOf<LocalDate?>(null) }
    val appContext = LocalContext.current.applicationContext
    BackHandler(enabled = selectedArea != MainArea.HOME) { selectedArea = MainArea.HOME }
    val events by attendanceRepository.events.collectAsState(initial = emptyList())
    val timeAdjustments by timeBalanceRepository.adjustments.collectAsState(initial = emptyList())
    val documents by documentRepository.documents.collectAsState(initial = emptyList())
    val workNotes by workNoteRepository.notes.collectAsState(initial = emptyList())
    val holidays by holidayRepository.holidays.collectAsState(initial = emptyList())
    val callIns by callInRepository.events.collectAsState(initial = emptyList())
    val bookedPtoDays by bookedPtoRepository.days.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
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
                                MainArea.HOME -> "HUBB HELPER"
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
                        request = calendarRequest,
                        onLogDate = { date -> logDate = date; showGlobalLog = true },
                        onUpdateAttendance = { updated ->
                            val existing = events.firstOrNull { it.id == updated.id }
                            if (existing != null) {
                                val oldContribution = attendanceContributionHalfPoints(existing.occurredOn, existing.type, existing.points, existing.status, appDate)
                                val newContribution = attendanceContributionHalfPoints(updated.occurredOn, updated.type, updated.points, updated.status, appDate)
                                onSetupDataChanged(setupData.withOpeningPointAdjustment(oldContribution - newContribution))
                                coroutineScope.launch { attendanceRepository.update(updated) }
                            }
                        },
                        onDeleteAttendance = { event ->
                            val contribution = attendanceContributionHalfPoints(event.occurredOn, event.type, event.points, event.status, appDate)
                            onSetupDataChanged(setupData.withOpeningPointAdjustment(contribution))
                            coroutineScope.launch { attendanceRepository.delete(event) }
                        },
                        onDeleteTimeAdjustment = { adjustment ->
                            coroutineScope.launch { timeBalanceRepository.delete(adjustment) }
                        },
                        onDeleteCallIn = { event ->
                            coroutineScope.launch { callInRepository.delete(event) }
                            saveCallInsRemaining(event.occurredOn.year, callInsRemainingFor(event.occurredOn.year) + 1)
                        },
                        onDeleteBookedPto = { day -> coroutineScope.launch { bookedPtoRepository.delete(day) } },
                        onDeleteHoliday = { holiday -> coroutineScope.launch { holidayRepository.delete(holiday) } },
                    ) }
                    MainArea.ATTENDANCE_DETAILS -> AttendanceDetailsScreen(
                        padding = padding,
                        appDate = appDate,
                        openingBalance = setupData.attendanceOpeningRemainder,
                        balancesAsOfDate = setupData.balancesAsOfDate,
                        events = events,
                        onViewRules = { selectedArea = MainArea.REFERENCE; referenceRootVersion++ },
                    )
                    MainArea.REFERENCE -> key(referenceRootVersion) { ContractLibraryScreen(padding, holidays) }
                    MainArea.DOCUMENTS -> DocumentLibraryScreen(
                        padding = padding,
                        documents = documents,
                        onImport = { uris, category ->
                            coroutineScope.launch {
                                val document = documentRepository.importPages(uris, category)
                                documentOcr.recognize(document)
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
                        onAddNote = { date, note -> coroutineScope.launch { workNoteRepository.add(date, note) } },
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
                                BackupExporter.export(
                                    context = appContext,
                                    destination = destination,
                                    setup = setupData,
                                    events = events,
                                    timeAdjustments = timeAdjustments,
                                    holidays = holidays,
                                    notes = workNotes,
                                    documents = documents,
                                    callIns = callIns,
                                    bookedPtoDays = bookedPtoDays,
                                )
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
                                    attendanceOpeningRemainder = desired.subtract(datedPoints).stripTrailingZeros().toPlainString(),
                                ),
                            )
                        },
                        onSetCallIns = { remaining -> saveCallInsRemaining(appDate.year, remaining) },
                        attendanceEvents = events,
                        onAddPastPoint = { date, type, points, status, note ->
                            val contribution = attendanceContributionHalfPoints(date, type, points, status, appDate)
                            onSetupDataChanged(setupData.withOpeningPointAdjustment(-contribution))
                            coroutineScope.launch { attendanceRepository.add(date, type, points, status, note) }
                        },
                        onUpdatePastPoint = { existing, updated ->
                            val oldContribution = attendanceContributionHalfPoints(
                                existing.occurredOn, existing.type, existing.points, existing.status, appDate,
                            )
                            val newContribution = attendanceContributionHalfPoints(
                                updated.occurredOn, updated.type, updated.points, updated.status, appDate,
                            )
                            onSetupDataChanged(setupData.withOpeningPointAdjustment(oldContribution - newContribution))
                            coroutineScope.launch { attendanceRepository.update(updated) }
                        },
                        onDeletePastPoint = { event ->
                            val contribution = attendanceContributionHalfPoints(
                                event.occurredOn, event.type, event.points, event.status, appDate,
                            )
                            onSetupDataChanged(setupData.withOpeningPointAdjustment(contribution))
                            coroutineScope.launch { attendanceRepository.delete(event) }
                        },
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
                        coroutineScope.launch { attendanceRepository.add(date, type, points, status, note) }
                    },
                    onTimeUsed = { date, kind, minutes, note ->
                        coroutineScope.launch { timeBalanceRepository.add(date, kind, minutes, note) }
                    },
                    callIns = callIns,
                    callInsRemainingForYear = ::callInsRemainingFor,
                    onCallIn = { date, ptoMinutes ->
                        coroutineScope.launch { callInRepository.add(date, ptoMinutes) }
                        saveCallInsRemaining(date.year, callInsRemainingFor(date.year) - 1)
                    },
                    bookedPtoDays = bookedPtoDays,
                    timeAdjustments = timeAdjustments,
                    birthdayMonth = setupData.birthdayMonth.toIntOrNull(),
                    onBookPto = { date, type -> coroutineScope.launch { bookedPtoRepository.add(date, type = type) } },
                    onNote = { date, note -> coroutineScope.launch { workNoteRepository.add(date, note) } },
                    onDocument = { uri, category ->
                        coroutineScope.launch {
                            val document = documentRepository.import(uri, category)
                            documentOcr.recognize(document)
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

@Composable
private fun HubNavigationBar(
    selectedArea: MainArea,
    onSelect: (MainArea) -> Unit,
    onLog: () -> Unit,
) {
    val design = HubThemeDesign.tokens
    val areas = listOf(MainArea.HOME, MainArea.CALENDAR, MainArea.REFERENCE, MainArea.DOCUMENTS)
    NavigationBar {
        areas.take(2).forEach { area ->
            HubNavigationItem(area, selectedArea == area) { onSelect(area) }
        }
        NavigationBarItem(
            selected = false,
            onClick = onLog,
            icon = {
                Surface(
                    modifier = Modifier.size(46.dp).semantics { contentDescription = "Log an event" },
                    shape = if (design.theme == HubTheme.INDUSTRIAL) androidx.compose.foundation.shape.CutCornerShape(10.dp) else androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            label = { Text("LOG") },
        )
        areas.drop(2).forEach { area ->
            HubNavigationItem(area, selectedArea == area) { onSelect(area) }
        }
    }
}

@Composable
private fun RowScope.HubNavigationItem(area: MainArea, selected: Boolean, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            HubNavIcon(
                area = area,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = area.label },
            )
        },
        label = {
            Text(
                when {
                    !largeFont -> area.shortLabel
                    area == MainArea.REFERENCE -> "Ref"
                    area == MainArea.DOCUMENTS -> "Docs"
                    else -> area.shortLabel
                },
            )
        },
    )
}

@Composable
private fun HubNavIcon(area: MainArea, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        when (area) {
            MainArea.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.48f)
                    lineTo(size.width * 0.5f, size.height * 0.16f)
                    lineTo(size.width * 0.88f, size.height * 0.48f)
                }
                drawPath(roof, color, style = stroke)
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.44f), size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.42f), style = stroke)
            }
            MainArea.CALENDAR, MainArea.ATTENDANCE_DETAILS -> {
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.68f), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.38f), strokeWidth = 1.8.dp.toPx())
                listOf(0.33f, 0.5f, 0.67f).forEach { x ->
                    listOf(0.52f, 0.68f).forEach { y ->
                        drawCircle(color, radius = 1.3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                    }
                }
            }
            MainArea.REFERENCE -> {
                val book = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.22f)
                    quadraticTo(size.width * 0.3f, size.height * 0.12f, size.width * 0.5f, size.height * 0.3f)
                    quadraticTo(size.width * 0.7f, size.height * 0.12f, size.width * 0.92f, size.height * 0.22f)
                    lineTo(size.width * 0.92f, size.height * 0.82f)
                    quadraticTo(size.width * 0.7f, size.height * 0.72f, size.width * 0.5f, size.height * 0.88f)
                    quadraticTo(size.width * 0.3f, size.height * 0.72f, size.width * 0.08f, size.height * 0.82f)
                    close()
                }
                drawPath(book, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.3f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.88f), strokeWidth = 1.8.dp.toPx())
            }
            MainArea.DOCUMENTS -> {
                val folder = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.28f)
                    lineTo(size.width * 0.4f, size.height * 0.28f)
                    lineTo(size.width * 0.5f, size.height * 0.4f)
                    lineTo(size.width * 0.92f, size.height * 0.4f)
                    lineTo(size.width * 0.84f, size.height * 0.82f)
                    lineTo(size.width * 0.08f, size.height * 0.82f)
                    close()
                }
                drawPath(folder, color, style = stroke)
            }
            else -> drawCircle(color, radius = 3.dp.toPx())
        }
    }
}

private fun attendanceContributionHalfPoints(
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

private fun SetupData.withOpeningPointAdjustment(halfPointDelta: Int): SetupData {
    val current = attendanceOpeningRemainder.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val delta = BigDecimal(halfPointDelta).divide(BigDecimal(2))
    return copy(attendanceOpeningRemainder = current.add(delta).stripTrailingZeros().toPlainString())
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    appDate: LocalDate,
    isDateOverridden: Boolean,
    setupData: SetupData,
    events: List<app.hubhelper.domain.AttendanceEvent>,
    timeAdjustments: List<app.hubhelper.domain.TimeBalanceAdjustment>,
    callIns: List<app.hubhelper.domain.CallInEvent>,
    callInsRemaining: Int,
    bookedPtoDays: List<app.hubhelper.domain.BookedPtoDay>,
    holidays: List<app.hubhelper.domain.PlantHoliday>,
    workNotes: List<app.hubhelper.domain.WorkNote>,
    onViewAttendanceDetails: () -> Unit,
    onViewCalendar: (CalendarRequest) -> Unit,
) {
    val design = HubThemeDesign.tokens
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val datedSummary = remember(events, appDate) { AttendanceCalculator().summarize(events, appDate) }
    val nextCreditDate = remember(events, appDate) { AttendanceCalculator().nextAttendanceCreditDate(events, appDate) }
    val opening = setupData.attendanceOpeningRemainder.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val dated = BigDecimal(datedSummary.confirmedPoints.value).divide(BigDecimal(2))
    val currentTotal = opening.add(dated).stripTrailingZeros().toPlainString()
    fun balance(kind: app.hubhelper.domain.TimeBalanceKind, openingHours: String): String {
        return TimeOffCalculator.balanceHours(
            kind = kind,
            enteredBalanceHours = openingHours,
            enteredBalanceDate = runCatching { LocalDate.parse(setupData.balancesAsOfDate) }.getOrDefault(appDate),
            hireDate = runCatching { LocalDate.parse(setupData.hireDate) }.getOrNull(),
            asOf = appDate,
            adjustments = timeAdjustments,
            callIns = callIns,
            bookedPtoDays = bookedPtoDays,
            regularBookedPtoMinutes = if (setupData.shiftPreset == "SECOND") 10 * 60 else 8 * 60,
        )
    }
    val ptoBalance = balance(app.hubhelper.domain.TimeBalanceKind.PTO, setupData.ptoBalanceHours)
    val sickBalance = balance(app.hubhelper.domain.TimeBalanceKind.SICK, setupData.sickBalanceHours)
    val nextHoliday = holidays.firstOrNull { !it.date.isBefore(appDate) }
    val total = opening.add(dated)
    val risk = when (app.hubhelper.domain.attendanceColorBand(total)) {
        app.hubhelper.domain.AttendanceColorBand.RED -> Triple("HIGH RISK", "Attendance points above five", MaterialTheme.colorScheme.error)
        app.hubhelper.domain.AttendanceColorBand.ORANGE -> Triple("WATCH", "Review upcoming changes", design.attention)
        app.hubhelper.domain.AttendanceColorBand.GREEN -> Triple("LOW RISK", "Good standing", design.good)
    }
    val expiringAmount = datedSummary.nextExpirationDate?.let { date ->
        events.filter {
            it.status == app.hubhelper.domain.AttendanceEventStatus.CONFIRMED &&
                it.type != app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT &&
                AttendanceCalculator().expiresOn(it) == date
        }.sumOf { it.points.value }
    } ?: 0
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = design.screenPadding, vertical = design.contentSpacing)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(design.contentSpacing),
    ) {
        if (isDateOverridden) {
            HubPanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.error) {
                Text(
                    "DEBUG DATE ACTIVE: $appDate",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HubPanel(Modifier.fillMaxWidth(), accent = risk.third, decorative = true) {
            SectionLabel("Attendance", color = risk.third)
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(currentTotal, style = design.metricLarge, color = risk.third)
                Text("  POINTS", style = MaterialTheme.typography.titleMedium, color = risk.third, modifier = Modifier.padding(bottom = 7.dp))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusBadge(risk.first, risk.third)
                Text(risk.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(13.dp))
            val nextChange: @Composable () -> Unit = {
                SectionLabel("Next change")
                val expirationDate = datedSummary.nextExpirationDate
                if (expirationDate != null) {
                    MetricValue(expirationDate.monthDayYear(), "−${app.hubhelper.domain.HalfPoints(expiringAmount).asDisplayValue()}")
                    Text("Point expires • 12-month rule", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("UNKNOWN", style = MaterialTheme.typography.titleMedium)
                    Text("Dated point details needed", style = MaterialTheme.typography.bodySmall)
                }
            }
            val estimatedCredit: @Composable () -> Unit = {
                SectionLabel("Estimated 90-day credit")
                when {
                    total <= BigDecimal(-1) -> Text("MAXIMUM −1", style = MaterialTheme.typography.titleMedium, color = design.good)
                    nextCreditDate != null -> MetricValue(nextCreditDate.monthDayYear(), color = design.good)
                    else -> Text("UNKNOWN", style = MaterialTheme.typography.titleMedium)
                }
                ProvenanceBadge("Estimated", Modifier.padding(top = 5.dp))
            }
            if (design.theme != HubTheme.CLEAR_EASY && !largeFont) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) { nextChange() }
                    Column(Modifier.weight(1f)) { estimatedCredit() }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.fillMaxWidth()) { nextChange() }
                    Column(Modifier.fillMaxWidth()) { estimatedCredit() }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onViewAttendanceDetails, modifier = Modifier.fillMaxWidth()) {
                Text("VIEW DETAILS  ›")
            }
        }

        val ptoWarningAt = if (setupData.shiftPreset == "SECOND") 10 else 8
        val ptoColor = if (TimeOffCalculator.isAtOrBelowOnePtoDay(ptoBalance, ptoWarningAt)) design.attention else design.pto
        val floatingRemaining = app.hubhelper.domain.remainingFloatingHolidays(
            timeAdjustments, bookedPtoDays, appDate, setupData.floatingHolidayAllowance.toIntOrNull() ?: 0,
        )
        if (!largeFont) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(design.contentSpacing)) {
            BalancePanel(
                "PTO hours", ptoBalance, "HRS", ptoColor, Modifier.weight(1f),
                { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.PTO)) },
                supporting = "As of ${appDate.monthDayYear()}",
                footer = "$floatingRemaining floating • opening ${setupData.ptoBalanceHours.ifBlank { "0" }} hrs",
            )
            BalancePanel(
                "Sick hours", sickBalance, "HRS", design.sick, Modifier.weight(1f),
                onClick = { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.SICK)) },
                supporting = "As of ${appDate.monthDayYear()}",
                footer = "opening ${setupData.sickBalanceHours.ifBlank { "0" }} hrs",
            )
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(design.contentSpacing)) {
            BalancePanel(
                "PTO hours", ptoBalance, "HRS", ptoColor, Modifier.fillMaxWidth(),
                { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.PTO)) },
                supporting = "As of ${appDate.monthDayYear()}",
                footer = "$floatingRemaining floating • opening ${setupData.ptoBalanceHours.ifBlank { "0" }} hrs",
            )
            BalancePanel(
                "Sick hours", sickBalance, "HRS", design.sick, Modifier.fillMaxWidth(),
                onClick = { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.SICK)) },
                supporting = "As of ${appDate.monthDayYear()}",
                footer = "opening ${setupData.sickBalanceHours.ifBlank { "0" }} hrs",
            )
        }
        CallInPanel(callInsRemaining, Modifier.fillMaxWidth()) {
            onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.CALL_IN))
        }

        HubPanel(Modifier.fillMaxWidth()) {
            val nextBooked = app.hubhelper.domain.nextBookedPto(bookedPtoDays, appDate)
            SectionLabel("Next booked vacation", color = design.pto)
            if (nextBooked == null) {
                Text("No upcoming PTO booked", style = MaterialTheme.typography.titleMedium)
                Text("Scan an exception form or log a booked PTO date.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nextBooked.date.monthDayYear(), style = MaterialTheme.typography.titleLarge, color = design.pto)
                    ProvenanceBadge(if (nextBooked.sourceDocumentId == null) "User" else "Source")
                }
                Text(when (nextBooked.type) {
                    app.hubhelper.domain.BookedTimeType.REGULAR_PTO -> "Vacation day booked"
                    app.hubhelper.domain.BookedTimeType.BIRTHDAY_FLOATING -> "Birthday-month floating holiday"
                    app.hubhelper.domain.BookedTimeType.ANYTIME_FLOATING -> "Anytime floating holiday"
                }, style = MaterialTheme.typography.bodySmall)
            }
        }

        HubPanel(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onViewCalendar(
                        CalendarRequest(nextHoliday?.date?.let(YearMonth::from) ?: YearMonth.from(appDate), CalendarFilter.HOLIDAY),
                    )
                },
        ) {
            SectionLabel("Next plant holiday", color = design.attention)
            if (nextHoliday == null) {
                Text("No reviewed holiday loaded", style = MaterialTheme.typography.titleMedium)
                Text("Add the annual plant calendar in Documents.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nextHoliday.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                    Text(nextHoliday.date.monthDayYear(), style = MaterialTheme.typography.titleLarge, color = design.attention)
                }
            }
        }

        HubPanel(
            Modifier
                .fillMaxWidth()
                .clickable { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.ALL)) },
        ) {
            SectionLabel("Recent activity")
            val attendanceRecent = events.sortedByDescending { it.occurredOn }.take(3)
            val timeRecent = timeAdjustments.sortedByDescending { it.occurredOn }.take(2)
            val noteRecent = workNotes.sortedByDescending { it.date }.take(1)
            val callInRecent = callIns.sortedByDescending { it.occurredOn }.take(1)
            if (attendanceRecent.isEmpty() && timeRecent.isEmpty() && noteRecent.isEmpty() && callInRecent.isEmpty()) {
                Text("No activity recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            attendanceRecent.forEach { event ->
                ActivityRow(event.occurredOn, event.type.name.lowercase().replace('_', ' '),
                    (if (event.type == app.hubhelper.domain.AttendanceEventType.ATTENDANCE_CREDIT) "−" else "+") + event.points.asDisplayValue(),
                    event.status.name.lowercase())
            }
            timeRecent.forEach { adjustment ->
                ActivityRow(adjustment.occurredOn, adjustment.kind.name, "${BigDecimal(adjustment.minutes).divide(BigDecimal(60)).stripTrailingZeros()} h", "user")
            }
            callInRecent.forEach { event -> ActivityRow(event.occurredOn, "call-in day", "−${event.ptoMinutes / 60} h PTO", "excused") }
            noteRecent.forEach { note -> ActivityRow(note.date, "work note", "", "user") }
            Text("OPEN CALENDAR  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("Unofficial employee reference • Original records remain authoritative", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

}

@Composable
private fun CallInPanel(
    remaining: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    HubPanel(modifier.clickable(onClick = onClick), accent = MaterialTheme.colorScheme.primary) {
        SectionLabel("Call-ins", color = MaterialTheme.colorScheme.primary)
        MetricValue(remaining.toString(), "LEFT", MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BalancePanel(
    title: String,
    balance: String,
    unit: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    supporting: String? = null,
    footer: String? = null,
) {
    HubPanel(modifier.clickable(onClick = onClick), accent = color) {
        SectionLabel(title, color = color)
        MetricValue(balance, unit, color)
        supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
    }
}

@Composable
private fun ActivityRow(date: LocalDate, label: String, change: String, status: String) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    if (!largeFont) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.monthDayYear(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.1f))
        Text(label.replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1.7f))
        Text(change, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(.7f))
        Text(status.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (status == "confirmed") HubThemeDesign.tokens.good else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    } else Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(date.monthDayYear(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(label.replaceFirstChar(Char::uppercase))
        Text(listOf(change, status.replaceFirstChar(Char::uppercase)).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NewYearResetDialog(
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
                Text("Scan the new plant holiday calendar so upcoming holidays remain accurate.")
            }
        },
        confirmButton = { Button(onClick = onScanCalendar) { Text("SCAN HOLIDAY CALENDAR") } },
        dismissButton = { TextButton(onClick = onContinue) { Text("Continue") } },
    )
}

@Composable
private fun SettingsScreen(
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
    onImportBackup: (Uri) -> Unit,
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
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
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
            Text("Edit hire date, shift, and starting balances")
        }
        BalanceCorrectionTools(onAddBalanceCorrection, onSetAttendancePoints, onSetCallIns)
        PastPointsTools(appDate, attendanceEvents, onAddPastPoint, onUpdatePastPoint, onDeletePastPoint)
        OutlinedButton(onClick = onOpenManual, modifier = Modifier.fillMaxWidth()) {
            Text("Open user manual")
        }
        OutlinedButton(
            onClick = { exportLauncher.launch("hubb-helper-backup.zip") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export private backup") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import backup") }
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
        Text("Hubb Helper 0.9.6 • build 30", style = MaterialTheme.typography.bodySmall)
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
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("IMPORT BACKUP?") },
            text = { Text("This adds the backup's records and original documents to the data already on this phone. Existing records are kept, so importing the same backup twice can create duplicates.") },
            confirmButton = {
                Button(onClick = { onImportBackup(uri); pendingImport = null }) { Text("IMPORT") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("RESET APP TO ZERO?") },
            text = { Text("This permanently removes attendance history, PTO and sick entries, call-ins, booked dates, holidays, notes, and saved documents. Current points, PTO, and sick balances will be set to zero. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onResetApp()
                    showResetConfirmation = false
                }) { Text("RESET EVERYTHING") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(initialTime: LocalTime, onDismiss: () -> Unit, onSet: (LocalTime) -> Unit) {
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
private fun PastPointsTools(
    appDate: LocalDate,
    events: List<app.hubhelper.domain.AttendanceEvent>,
    onAdd: (LocalDate, app.hubhelper.domain.AttendanceEventType, app.hubhelper.domain.HalfPoints, app.hubhelper.domain.AttendanceEventStatus, String?) -> Unit,
    onUpdate: (app.hubhelper.domain.AttendanceEvent, app.hubhelper.domain.AttendanceEvent) -> Unit,
    onDelete: (app.hubhelper.domain.AttendanceEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<app.hubhelper.domain.AttendanceEvent?>(null) }
    var deleting by remember { mutableStateOf<app.hubhelper.domain.AttendanceEvent?>(null) }

    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Close past-points editor" else "Add, edit, or remove past points")
    }
    if (expanded) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Past points and falloffs", style = MaterialTheme.typography.titleMedium)
                Text("These dated entries schedule falloffs. They do not change the current total you entered manually.", style = MaterialTheme.typography.bodySmall)
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
private fun BalanceCorrectionTools(
    onSave: (app.hubhelper.domain.TimeBalanceKind, Int, String?) -> Unit,
    onSetAttendancePoints: (String) -> Unit,
    onSetCallIns: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(app.hubhelper.domain.TimeBalanceKind.PTO) }
    var addHours by remember { mutableStateOf(true) }
    var hoursText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("PTO") }
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

@Composable
private fun UserManualScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ManualSection(
            "About Hubb Helper",
            "Hubb Helper is a private, unofficial work reference. It is not affiliated with or endorsed by Hubbell, the IBEW, or the IAM. Original documents remain the authority.",
        )
        ManualSection(
            "Getting started",
            "Enter your hire date, shift, current PTO and sick balances, call-ins remaining, and current attendance points. Current points are always entered manually. Setup remains editable under Settings. Home panels open the Calendar with the matching month and event type highlighted.",
        )
        ManualSection(
            "Attendance calculations",
            "The dashboard starts from the current point total you enter manually. Dated confirmed point entries identify individual 12-month falloffs; adding, editing, or removing past points in Settings keeps today's manual total unchanged. Pending, disputed, excused, and rescinded entries stay visible without changing calculations. Enter verified 90-day credits as credit events; estimated credit dates never create credits automatically.",
        )
        ManualSection(
            "Documents and printouts",
            "Tap Add document, choose its category, then take photos or choose files from your phone. Attendance intake accepts multiple pages by default and saves all pages from one scan or selection as a single document. Confirmed attendance-sheet rows become permanent dated records used by the Calendar and point-falloff calculations; they never set the current point total. Re-scanning the same rows skips duplicates and reports the result. Review detected rows before confirming them. Exception forms can propose booked vacation dates, and holiday calendars can propose dated plant holidays; review each result before saving. Reading PDF pages remains under development.",
        )
        ManualSection(
            "PTO, sick time, holidays, and notes",
            "Opening balances come from setup. Use LOG to record full-day, half-day, or custom PTO, sick time, call-ins, floating holidays, and booked vacation. Sick time is shown as days; one sick day is eight hours. You receive five call-ins each calendar year. Logging one deducts eight PTO hours on first shift or ten on second shift. A regular booked vacation deducts the shift-day amount when its date arrives and is not deducted twice if it was also recorded manually. Setup asks how many floating vacation days are available; those personal days are tracked separately and are never listed as dated plant holidays. Reviewed plant holidays also appear in Reference.",
        )
        ManualSection(
            "Calendar",
            "Calendar opens with all twelve months. A stronger red month indicator means more confirmed attendance points were accrued in that month; the printed point value remains authoritative. Tap a month to see its days. The pinned legend identifies green point falloffs, confirmed and estimated attendance credits, accrued points, call-ins, sick time, PTO, and plant holidays. Estimated credits use an outlined marker. Tap a legend item to filter, tap a day for full details, or log something with that date already selected. Editable entries can be changed or removed from the day details. Manually entered points without dates cannot appear on the calendar; confirmed attendance-sheet rows do have dates and do appear.",
        )
        ManualSection(
            "Search",
            "Press the keyboard Search action or the search button. Results appear directly below the search box and can be tapped to open the matching reference or saved document. OCR results remain derived text and should be checked against the original.",
        )
        ManualSection(
            "Reminders and backup",
            "Settings can schedule a weekly check-in, enable app lock, correct balances, set current points and call-ins, manage past points individually, and export or import a ZIP containing structured records plus original documents. Exported files are outside the app's protection, so store them securely. Reset app to zero requires confirmation, clears all records and documents, zeros current balances, and returns you to Settings.",
        )
        ManualSection(
            "Debug date",
            "Debug builds include a date override under Settings. Use it to test falloffs without changing the phone clock. A visible warning appears while the override is active; reset it with Use device date.",
        )
        ManualSection(
            "Privacy",
            "Data stays in app-private storage. Cloud backup and network access are disabled. Export is explicit and user initiated. Theme changes are stored only on this device.",
        )
        ManualSection(
            "Appearance",
            "Settings offers Industrial Instrument, Clear & Easy, and Soft & Friendly. Industrial uses compact chamfered instrument panels, Clear & Easy prioritizes larger text and obvious controls, and Soft & Friendly uses rounded forms and a calm palette. Each theme supports Follow system, Light, and Dark modes. All fonts are bundled for offline use.",
        )
        Text("Manual for Hubb Helper 0.9.6", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ManualSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body)
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun dateWithCountdown(date: LocalDate, today: LocalDate, canBeOverdue: Boolean): String {
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
private fun EmptyArea(padding: PaddingValues, title: String, explanation: String) {
    Column(modifier = Modifier.padding(padding).padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(explanation)
    }
}
