package app.hubhelper

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import app.hubhelper.data.HubHelperDatabase
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
        val operations = androidx.lifecycle.ViewModelProvider(this)[AppOperations::class.java]
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
        var lockEnabled by mutableStateOf(appLockPreferences.enabled)
        unlocked = !appLockPreferences.enabled
        if (appLockPreferences.enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
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
        val database = HubHelperDatabase.get(this)
        val setupStore = SetupStore(database)
        var setupData by mutableStateOf(setupPreferences.load())
        var initialized by mutableStateOf(false)
        var initializationError by mutableStateOf<String?>(null)
        var showSetup by mutableStateOf(!setupPreferences.isComplete)
        var reminderPreference by mutableStateOf(reminderPreferences.load(setupData.shiftPreset))
        var selectedTheme by mutableStateOf(themePreferences.theme)
        var themeMode by mutableStateOf(themePreferences.mode)
        val setupYear = runCatching { LocalDate.parse(setupData.balancesAsOfDate).year }.getOrDefault(LocalDate.now().year)
        var lastAcknowledgedYear by mutableStateOf(newYearPreferences.lastAcknowledgedYear(setupYear))
        if (reminderPreference.enabled) WeeklyReminderScheduler.apply(this, reminderPreference)

        lifecycleScope.launch {
            try {
                DatabaseBackup.recover(this@MainActivity)
                setupStore.initialize(setupPreferences)
                val saved = setupStore.read()
                if (saved != null) { setupData = saved; showSetup = false }
                initialized = true
                setupStore.data.collect { savedData -> if (savedData != null) setupData = savedData }
            } catch (error: Exception) { initializationError = error.message ?: "Unable to open records" }
        }
        setContent {
            if (!initialized) {
                androidx.compose.material3.Text(initializationError ?: "Opening your records…")
                return@setContent
            }
            val appScope = operations

            var setupAttendancePreview by remember { mutableStateOf<SetupAttendancePreview?>(null) }
            var deviceDate by remember { mutableStateOf(LocalDate.now()) }
            LaunchedEffect(Unit) { while (true) { deviceDate = LocalDate.now(); kotlinx.coroutines.delay(30_000) } }
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
                        lifecycleScope.launch {
                            runCatching { setupStore.save(data) }.onSuccess { setupData = data; showSetup = false }
                                .onFailure { Toast.makeText(this@MainActivity, "Setup was not saved: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
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
                    operations = operations,
                    appDate = overrideDate ?: deviceDate,
                    overrideDate = overrideDate,
                    onDateOverrideChanged = { date ->
                        val normalized = date?.takeUnless { it == LocalDate.now() }
                        debugDateController.setOverride(normalized)
                        overrideDate = normalized
                    },
                    setupData = setupData,
                    onSetupDataChanged = { data ->
                        appScope.launch {
                            runCatching { setupStore.save(data) }.onFailure { Toast.makeText(this@MainActivity, "Changes were not saved: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
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
                            val importResult = database.withTransaction {
                            val result = applyAttendanceStatement(
                                setupData, parsed, document.id, overrideDate ?: LocalDate.now(), attendanceRepository,
                            )
                            setupStore.save(result.setup, "attendance reconciliation")
                            result
                            }
                            setupData = importResult.setup
                            Toast.makeText(
                                this@MainActivity,
                                "Attendance sheet confirmed: ${importResult.addedCount} saved, ${importResult.skippedCount} already present. Reported balance reconciled through ${parsed.statementDate ?: (overrideDate ?: LocalDate.now())}.",
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
                    appLockEnabled = lockEnabled,
                    onAppLockChanged = { enabled ->
                        val canAuthenticate = androidx.biometric.BiometricManager.from(this@MainActivity).canAuthenticate(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        if (enabled && canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                            Toast.makeText(this@MainActivity, "Set up a supported device screen lock first", Toast.LENGTH_LONG).show()
                        } else {
                        appLockPreferences.enabled = enabled
                        lockEnabled = enabled
                        if (enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        if (!enabled) unlocked = true
                        }
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
                    onImportBackup = { source, replace ->
                        appScope.launch {
                            runCatching {
                                BackupImporter.import(
                                    context = this@MainActivity,
                                    source = source,
                                    replace = replace,
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
                                androidx.work.WorkManager.getInstance(this@MainActivity).cancelAllWork().result.get()
                                app.hubhelper.data.StorageCoordinator.mutex.lock()
                                try {
                                AppDataResetter.clearAll(this@MainActivity)
                                File(filesDir, "documents").deleteRecursively()
                                listOf("camera-captures", "backup-import", "ocr-pages").forEach { File(cacheDir, it).deleteRecursively() }
                                app.hubhelper.data.RestoreJournal(this@MainActivity).delete()
                                listOf("weekly_reminder", "new_year_review", "backup_status").forEach { getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }
                                } finally { app.hubhelper.data.StorageCoordinator.mutex.unlock() }
                            }
                            setupPreferences.reset()
                            appLockPreferences.enabled = false
                            lockEnabled = false
                            unlocked = true
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                            reminderPreference = ReminderPreference()
                            debugDateController.setOverride(null)
                            overrideDate = null
                            setupData = SetupData()
                            showSetup = true
                            Toast.makeText(this@MainActivity, "All app data was deleted. Starting first-time setup.", Toast.LENGTH_LONG).show()
                        }
                    },
                    lastAcknowledgedYear = lastAcknowledgedYear,
                    onYearAcknowledged = { year ->
                        newYearPreferences.acknowledge(year)
                        lastAcknowledgedYear = year
                    },
                )
            }
            OperationStatus(operations)
            setupAttendancePreview?.let { preview ->
                AttendanceImportPreviewDialog(
                    parsed = preview.parsed,
                    calculationDate = overrideDate ?: deviceDate,
                    sourceDocumentId = preview.documentId,
                    manualTotal = setupData.currentAttendancePoints,
                    onDismiss = {
                        setupPreferences.markPointsSheetImported(preview.uriText)
                        setupAttendancePreview = null
                    },
                    onConfirm = { reviewed ->
                        appScope.launch {
                            val result = database.withTransaction {
                            val reconciled = applyAttendanceStatement(
                                setupData, reviewed, preview.documentId, overrideDate ?: LocalDate.now(), attendanceRepository,
                            )
                            setupStore.save(reconciled.setup, "attendance reconciliation")
                            reconciled
                            }
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
