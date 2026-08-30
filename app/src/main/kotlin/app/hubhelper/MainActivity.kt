package app.hubhelper

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import app.hubhelper.data.AttendanceRepository
import app.hubhelper.data.TimeBalanceRepository
import app.hubhelper.data.DocumentRepository
import app.hubhelper.data.WorkNoteRepository
import app.hubhelper.data.HolidayRepository
import app.hubhelper.data.CallInRepository
import app.hubhelper.data.BookedPtoRepository
import app.hubhelper.domain.DocumentCategory
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.AttendancePrintoutParser
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.HalfPoints
import app.hubhelper.domain.ParsedAttendanceStatement
import android.net.Uri
import android.widget.Toast
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import app.hubhelper.data.AppDataResetter

class MainActivity : FragmentActivity() {
    private lateinit var appLockPreferences: AppLockPreferences
    private lateinit var biometricPrompt: BiometricPrompt
    private var unlocked by mutableStateOf(true)
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val debugDateController = createDebugDateController(this)
        val setupPreferences = SetupPreferences(this)
        val attendanceRepository = AttendanceRepository.create(this)
        val timeBalanceRepository = TimeBalanceRepository.create(this)
        val documentRepository = DocumentRepository.create(this)
        val workNoteRepository = WorkNoteRepository.create(this)
        val holidayRepository = HolidayRepository.create(this)
        val callInRepository = CallInRepository.create(this)
        val bookedPtoRepository = BookedPtoRepository.create(this)
        val documentOcr = DocumentOcr(this, documentRepository)
        val reminderPreferences = ReminderPreferences(this)
        val themePreferences = ThemePreferences(this)
        val newYearPreferences = NewYearPreferences(this)
        appLockPreferences = AppLockPreferences(this)
        unlocked = !appLockPreferences.enabled
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }
            },
        )
        var overrideDate by mutableStateOf(debugDateController.overrideDate)
        var setupData by mutableStateOf(setupPreferences.load())
        var showSetup by mutableStateOf(!setupPreferences.isComplete)
        var reminderPreference by mutableStateOf(reminderPreferences.load(setupData.shiftPreset))
        var selectedTheme by mutableStateOf(themePreferences.theme)
        var themeMode by mutableStateOf(themePreferences.mode)
        val setupYear = runCatching { LocalDate.parse(setupData.balancesAsOfDate).year }.getOrDefault(LocalDate.now().year)
        var lastAcknowledgedYear by mutableStateOf(newYearPreferences.lastAcknowledgedYear(setupYear))
        if (reminderPreference.enabled) WeeklyReminderScheduler.apply(this, reminderPreference)

        setContent {
            val appScope = rememberCoroutineScope()
            var setupAttendancePreview by remember { mutableStateOf<SetupAttendancePreview?>(null) }
            val darkMode = themeMode.resolveDarkMode(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
            if (!unlocked) {
                AppLockedScreen(selectedTheme, darkMode, ::requestUnlock)
            } else if (showSetup) {
                InitialSetupScreen(
                    theme = selectedTheme,
                    darkMode = darkMode,
                    initialData = setupData,
                    canCancel = setupPreferences.isComplete,
                    onComplete = { data ->
                        setupPreferences.save(data)
                        setupData = data
                        showSetup = false
                        data.pointsSheetUri?.takeIf(setupPreferences::needsPointsSheetImport)?.let { uriText ->
                            appScope.launch {
                                runCatching {
                                    val pageUris = uriText.lineSequence().filter(String::isNotBlank).map(Uri::parse).toList()
                                    val document = documentRepository.importPages(pageUris, DocumentCategory.ATTENDANCE)
                                    val recognizedText = documentOcr.recognize(document)
                                    val parsed = recognizedText?.let { AttendancePrintoutParser().parse(it) }
                                    if (parsed != null) {
                                        setupAttendancePreview = SetupAttendancePreview(document.id, parsed, uriText)
                                    } else {
                                        setupPreferences.markPointsSheetImported(uriText)
                                        Toast.makeText(this@MainActivity, "No attendance rows were recognized. Try clearer photos.", Toast.LENGTH_LONG).show()
                                    }
                                }.onFailure { error ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Attendance scan failed: ${error.message ?: "please try a clearer photo"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                    onCancel = { showSetup = false },
                )
            } else {
                HubHelperApp(
                    appDate = overrideDate ?: LocalDate.now(),
                    overrideDate = overrideDate,
                    onDateOverrideChanged = { date ->
                        debugDateController.setOverride(date)
                        overrideDate = date
                    },
                    setupData = setupData,
                    onSetupDataChanged = { data ->
                        setupPreferences.save(data)
                        setupData = data
                    },
                    attendanceRepository = attendanceRepository,
                    timeBalanceRepository = timeBalanceRepository,
                    documentRepository = documentRepository,
                    workNoteRepository = workNoteRepository,
                    holidayRepository = holidayRepository,
                    callInRepository = callInRepository,
                    bookedPtoRepository = bookedPtoRepository,
                    documentOcr = documentOcr,
                    onEditSetup = { showSetup = true },
                    onApplyAttendanceStatement = { document, parsed ->
                        appScope.launch {
                            val importResult = applyAttendanceStatement(
                                setupData, parsed, document.id, overrideDate ?: LocalDate.now(), attendanceRepository,
                            )
                            setupPreferences.save(importResult.setup)
                            setupData = importResult.setup
                            Toast.makeText(
                                this@MainActivity,
                                "Attendance sheet confirmed: ${importResult.addedCount} saved, ${importResult.skippedCount} already present. Current points were not changed.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    reminderPreference = reminderPreference,
                    onReminderChanged = { preference ->
                        reminderPreferences.save(preference)
                        WeeklyReminderScheduler.apply(this, preference)
                        reminderPreference = preference
                    },
                    appLockEnabled = appLockPreferences.enabled,
                    onAppLockChanged = { enabled ->
                        appLockPreferences.enabled = enabled
                        if (!enabled) unlocked = true
                    },
                    selectedTheme = selectedTheme,
                    onThemeChanged = { theme ->
                        themePreferences.theme = theme
                        selectedTheme = theme
                    },
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themePreferences.mode = mode
                        themeMode = mode
                    },
                    darkMode = darkMode,
                    onImportBackup = { source ->
                        appScope.launch {
                            runCatching {
                                BackupImporter.import(
                                    context = this@MainActivity,
                                    source = source,
                                    currentSetup = setupData,
                                    attendanceRepository = attendanceRepository,
                                    timeBalanceRepository = timeBalanceRepository,
                                    holidayRepository = holidayRepository,
                                    workNoteRepository = workNoteRepository,
                                    documentRepository = documentRepository,
                                    callInRepository = callInRepository,
                                    bookedPtoRepository = bookedPtoRepository,
                                )
                            }.onSuccess { result ->
                                setupPreferences.save(result.setup)
                                setupData = result.setup
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup imported: ${result.attendanceCount} attendance, ${result.callInCount} call-ins, ${result.timeCount} time, ${result.documentCount} documents.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup import failed: ${error.message ?: "invalid backup"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onResetApp = {
                        appScope.launch {
                            withContext(Dispatchers.IO) {
                                AppDataResetter.clearAll(this@MainActivity)
                                File(filesDir, "documents").deleteRecursively()
                            }
                            val reset = setupData.copy(
                                ptoBalanceHours = "0",
                                sickBalanceHours = "0",
                                currentAttendancePoints = "0",
                                attendanceOpeningRemainder = "0",
                                pointsSheetUri = null,
                                balancesAsOfDate = (overrideDate ?: LocalDate.now()).toString(),
                                callInsRemaining = app.hubhelper.domain.ANNUAL_CALL_IN_ALLOWANCE.toString(),
                                callInsBalanceYear = (overrideDate ?: LocalDate.now()).year.toString(),
                            )
                            setupPreferences.save(reset)
                            setupData = reset
                            Toast.makeText(this@MainActivity, "App records reset to zero.", Toast.LENGTH_LONG).show()
                        }
                    },
                    lastAcknowledgedYear = lastAcknowledgedYear,
                    onYearAcknowledged = { year ->
                        newYearPreferences.acknowledge(year)
                        lastAcknowledgedYear = year
                    },
                )
            }
            setupAttendancePreview?.let { preview ->
                AttendanceImportPreviewDialog(
                    parsed = preview.parsed,
                    manualTotal = setupData.currentAttendancePoints,
                    onDismiss = {
                        setupPreferences.markPointsSheetImported(preview.uriText)
                        setupAttendancePreview = null
                    },
                    onConfirm = {
                        appScope.launch {
                            val result = applyAttendanceStatement(
                                setupData, preview.parsed, preview.documentId, overrideDate ?: LocalDate.now(), attendanceRepository,
                            )
                            setupPreferences.save(result.setup)
                            setupData = result.setup
                            setupPreferences.markPointsSheetImported(preview.uriText)
                            setupAttendancePreview = null
                            Toast.makeText(this@MainActivity, "Attendance confirmed: ${result.addedCount} saved, ${result.skippedCount} already present.", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::appLockPreferences.isInitialized && appLockPreferences.enabled && !unlocked) requestUnlock()
    }

    override fun onStop() {
        super.onStop()
        if (::appLockPreferences.isInitialized && appLockPreferences.enabled) unlocked = false
        promptShowing = false
    }

    private fun requestUnlock() {
        if (promptShowing || !appLockPreferences.enabled) return
        promptShowing = true
        biometricPrompt.authenticate(appLockPromptInfo())
    }
}

private data class SetupAttendancePreview(
    val documentId: String,
    val parsed: ParsedAttendanceStatement,
    val uriText: String,
)

private fun importedAttendanceType(comment: String, adjustmentHalfPoints: Int): AttendanceEventType {
    if (adjustmentHalfPoints < 0) return AttendanceEventType.ATTENDANCE_CREDIT
    val normalized = comment.lowercase()
    return when {
        "tardy" in normalized || "late" in normalized -> AttendanceEventType.TARDY
        "left" in normalized || "early" in normalized -> AttendanceEventType.LEFT_EARLY
        "call" in normalized -> AttendanceEventType.CALL_IN_VIOLATION
        else -> AttendanceEventType.UNEXCUSED_ABSENCE
    }
}

private fun displayHalfPoints(halfPoints: Int): String = HalfPoints(halfPoints).asDisplayValue()

private data class AttendanceImportResult(
    val setup: SetupData,
    val addedCount: Int,
    val skippedCount: Int,
)

private suspend fun applyAttendanceStatement(
    setup: SetupData,
    parsed: ParsedAttendanceStatement,
    documentId: String,
    calculationDate: LocalDate,
    repository: AttendanceRepository,
): AttendanceImportResult {
    val usableRows = parsed.rows.filter {
        it.adjustmentHalfPoints != null && it.adjustmentHalfPoints != 0 && !isAnnualFalloff(it.comment)
    }
    var importedActiveTotal = 0
    var addedCount = 0
    usableRows.forEach { row ->
        val adjustment = row.adjustmentHalfPoints!!
        val added = repository.addIfAbsent(
            occurredOn = row.date,
            type = importedAttendanceType(row.comment, adjustment),
            points = HalfPoints(kotlin.math.abs(adjustment)),
            status = AttendanceEventStatus.CONFIRMED,
            note = row.comment,
            sourceDocumentId = documentId,
        )
        if (added) {
            addedCount++
            if (!row.date.isAfter(calculationDate) &&
            (adjustment < 0 || calculationDate.isBefore(row.date.plusMonths(12)))
            ) importedActiveTotal += adjustment
        }
    }
    val openingHalfPoints = setup.attendanceOpeningRemainder.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(2))
        ?.toInt()
        ?: 0
    val manualTotal = setup.currentAttendancePoints.toBigDecimalOrNull()
        ?.multiply(java.math.BigDecimal(2))
        ?.toInt()
    val sheetTotal = manualTotal ?: parsed.currentTotalHalfPoints
    if (sheetTotal != null) {
        val datedPoints = AttendanceCalculator().summarize(repository.allEvents(), calculationDate).confirmedPoints.value
        val reconciledOpening = sheetTotal - datedPoints
        return AttendanceImportResult(
            setup = setup.copy(
                currentAttendancePoints = HalfPoints(sheetTotal).asDisplayValue(),
                attendanceOpeningRemainder = displayHalfPoints(reconciledOpening),
            ),
            addedCount = addedCount,
            skippedCount = usableRows.size - addedCount,
        )
    }
    return AttendanceImportResult(
        setup = setup.copy(attendanceOpeningRemainder = displayHalfPoints(openingHalfPoints - importedActiveTotal)),
        addedCount = addedCount,
        skippedCount = usableRows.size - addedCount,
    )
}

private fun isAnnualFalloff(comment: String): Boolean {
    val normalized = comment.lowercase()
    return ("1 year" in normalized || "one year" in normalized || "annual" in normalized) &&
        ("roll" in normalized || "fall" in normalized)
}
