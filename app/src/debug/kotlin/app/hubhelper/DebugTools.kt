package app.hubhelper

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.HalfPoints
import java.time.LocalDate

private const val PREFERENCES = "debug_date"
private const val OVERRIDE_KEY = "override"

internal fun platformDebugDateController(context: Context): DebugDateController =
    object : DebugDateController {
        private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        override val overrideDate: LocalDate?
            get() = preferences.getString(OVERRIDE_KEY, null)?.let(LocalDate::parse)

        override fun setOverride(date: LocalDate?) {
            preferences.edit().apply {
                if (date == null) remove(OVERRIDE_KEY) else putString(OVERRIDE_KEY, date.isoDate())
            }.apply()
        }
    }

@Composable
fun DebugTools(
    appDate: LocalDate,
    overrideDate: LocalDate?,
    onOverrideChanged: (LocalDate?) -> Unit,
) {
    var selectedDate by remember(overrideDate) { mutableStateOf(overrideDate ?: appDate) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug date", style = MaterialTheme.typography.titleMedium)
            Text("Override the date used by app calculations. This setting persists across restarts.")
            DatePickerField("App date", selectedDate, { it?.let { chosen -> selectedDate = chosen } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOverrideChanged(selectedDate) }) {
                    Text("Apply")
                }
                OutlinedButton(onClick = {
                    val changed = appDate.minusDays(1)
                    selectedDate = changed
                    onOverrideChanged(changed)
                }) { Text("−1 day") }
                OutlinedButton(onClick = {
                    val changed = appDate.plusDays(1)
                    selectedDate = changed
                    onOverrideChanged(changed)
                }) { Text("+1 day") }
            }
            OutlinedButton(
                onClick = {
                    selectedDate = LocalDate.now()
                    onOverrideChanged(null)
                },
                enabled = overrideDate != null,
            ) { Text("Use device date") }
        }
    }

    FalloffPreview(appDate)
}

@Composable
private fun FalloffPreview(appDate: LocalDate) {
    var issuedDate by remember { mutableStateOf(appDate.minusMonths(12)) }
    val calculator = remember { AttendanceCalculator() }
    val expiration = issuedDate.let {
        calculator.expiresOn(
            AttendanceEvent(
                id = "debug-preview",
                occurredOn = it,
                type = AttendanceEventType.UNEXCUSED_ABSENCE,
                points = HalfPoints(2),
                status = AttendanceEventStatus.CONFIRMED,
            ),
        )
    }
    val isActive = appDate.isBefore(expiration)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Point falloff preview", style = MaterialTheme.typography.titleMedium)
            DatePickerField("Point issued", issuedDate, { it?.let { chosen -> issuedDate = chosen } })
            Text("Falls off: ${expiration.monthDayYear()}")
            Text(
                if (isActive) "ACTIVE on ${appDate.monthDayYear()}" else "EXPIRED on ${appDate.monthDayYear()}",
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
