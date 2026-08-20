package app.hubhelper

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.util.UUID

@Composable
fun InitialSetupScreen(
    darkMode: Boolean,
    initialData: SetupData,
    canCancel: Boolean,
    onComplete: (SetupData) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var ptoBalance by remember(initialData) { mutableStateOf(initialData.ptoBalanceHours) }
    var sickBalance by remember(initialData) { mutableStateOf(sickDaysFromHours(initialData.sickBalanceHours)) }
    var currentPoints by remember(initialData) { mutableStateOf(initialData.currentAttendancePoints) }
    var callInsRemaining by remember(initialData) { mutableStateOf(initialData.callInsRemaining) }
    var shiftPreset by remember(initialData) { mutableStateOf(initialData.shiftPreset) }
    var pointsSheetUris by remember(initialData) {
        mutableStateOf(initialData.pointsSheetUri?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty())
    }
    var hireDate by remember(initialData) { mutableStateOf(runCatching { LocalDate.parse(initialData.hireDate) }.getOrNull()) }
    var birthdayMonth by remember(initialData) { mutableStateOf(initialData.birthdayMonth.toIntOrNull()) }
    var cameraUri by remember { mutableStateOf(newSetupCameraUri(context)) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pointsSheetUris = pointsSheetUris + cameraUri.toString()
    }

    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uris.isNotEmpty()) pointsSheetUris = uris.map(Uri::toString)
    }

    val ptoValid = ptoBalance.isBlank() || ptoBalance.toBigDecimalOrNull()?.let { it.signum() >= 0 } == true
    val sickValid = sickBalance.isBlank() || sickBalance.toBigDecimalOrNull()?.let { it.signum() >= 0 } == true
    val parsedPoints = currentPoints.toBigDecimalOrNull()
    val pointsValid = currentPoints.isBlank() || parsedPoints?.let {
        it >= (-1).toBigDecimal() && it.remainder("0.5".toBigDecimal()).signum() == 0
    } == true
    val callInsValid = callInsRemaining.isBlank() || callInsRemaining.toIntOrNull() in 0..5

    HubHelperTheme(darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Set up Hubb Helper", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Add what you know now. Every item can be skipped and changed later in Settings.")

                Spacer(Modifier.height(4.dp))
                Text("Work shift", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shiftPreset == "FIRST",
                        onClick = { shiftPreset = "FIRST" },
                        label = { Text("First shift") },
                    )
                    FilterChip(
                        selected = shiftPreset == "SECOND",
                        onClick = { shiftPreset = "SECOND" },
                        label = { Text("Second shift") },
                    )
                }
                DatePickerField("Hire date", hireDate, { selected ->
                    if (selected == null || !selected.isAfter(LocalDate.now())) hireDate = selected
                }, allowClear = true)
                Text("Birthday month", style = MaterialTheme.typography.titleMedium)
                Text("Used only to identify which floating holiday is limited to your birthday month.", style = MaterialTheme.typography.bodySmall)
                Month.entries.chunked(4).forEach { months ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        months.forEach { month ->
                            FilterChip(
                                selected = birthdayMonth == month.value,
                                onClick = { birthdayMonth = month.value },
                                label = { Text(month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)) },
                            )
                        }
                    }
                }
                BalanceField("Current PTO balance (hours)", ptoBalance, ptoValid) { ptoBalance = it }
                BalanceField("Current sick balance (days)", sickBalance, sickValid) { sickBalance = it }
                OutlinedTextField(
                    value = callInsRemaining,
                    onValueChange = { callInsRemaining = it.filter(Char::isDigit).take(1) },
                    label = { Text("Call-in days remaining this year") },
                    placeholder = { Text("Example: 5") },
                    isError = !callInsValid,
                    supportingText = { Text("Enter a whole number from 0 to 5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Attendance points sheet", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = currentPoints,
                    onValueChange = { currentPoints = it },
                    label = { Text("Current attendance points") },
                    placeholder = { Text("Example: 3.5") },
                    isError = !pointsValid,
                    supportingText = {
                        Text(
                            if (pointsValid) "Enter the current total manually"
                            else "Use whole or half points; minimum is −1",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Choose a clear photo or PDF to identify the dates when past points fall off. Images are read on-device. Attendance sheets never set your current point total.")
                OutlinedButton(
                    onClick = { sheetPicker.launch(arrayOf("image/*", "application/pdf")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pointsSheetUris.isEmpty()) "Choose attendance pages" else "Replace attendance pages")
                }
                OutlinedButton(
                    onClick = {
                        cameraUri = newSetupCameraUri(context)
                        cameraLauncher.launch(cameraUri)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (pointsSheetUris.isEmpty()) "Photograph first page" else "Photograph another page") }
                if (pointsSheetUris.isNotEmpty()) {
                    Text("${pointsSheetUris.size} attendance page${if (pointsSheetUris.size == 1) "" else "s"} selected as one document", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { pointsSheetUris = emptyList() }) { Text("Remove all pages") }
                } else {
                    Text("You can scan or photograph it later.", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onComplete(
                            SetupData(
                                ptoBalanceHours = ptoBalance.trim(),
                                sickBalanceHours = sickHoursFromDays(sickBalance.trim()),
                                currentAttendancePoints = currentPoints.trim(),
                                attendanceOpeningRemainder = if (
                                    currentPoints.trim() == initialData.currentAttendancePoints &&
                                    pointsSheetUris.joinToString("\n") == initialData.pointsSheetUri
                                ) initialData.attendanceOpeningRemainder else currentPoints.trim(),
                                shiftPreset = shiftPreset,
                                pointsSheetUri = pointsSheetUris.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                                hireDate = hireDate?.toString().orEmpty(),
                                balancesAsOfDate = initialData.balancesAsOfDate,
                                callInsRemaining = callInsRemaining.trim(),
                                callInsBalanceYear = if (callInsRemaining.isBlank()) "" else LocalDate.now().year.toString(),
                                birthdayMonth = birthdayMonth?.toString().orEmpty(),
                            ),
                        )
                    },
                    enabled = ptoValid && sickValid && pointsValid && callInsValid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Finish setup") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canCancel) {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    } else {
                        OutlinedButton(onClick = { onComplete(SetupData()) }) { Text("Set up later") }
                    }
                }
            }
        }
    }
}

private fun newSetupCameraUri(context: android.content.Context): Uri {
    val file = File(context.cacheDir, "camera-captures/setup-points-sheet-${UUID.randomUUID()}.jpg").apply {
        parentFile?.mkdirs()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
private fun BalanceField(
    label: String,
    value: String,
    valid: Boolean,
    onValueChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        placeholder = { Text("Optional") },
        isError = !valid,
        supportingText = { if (!valid) Text("Enter zero or a positive number") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
