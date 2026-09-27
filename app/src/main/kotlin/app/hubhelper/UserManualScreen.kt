package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun UserManualScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ManualSection(
            "About Hub Helper",
            "Hub Helper is a private, unofficial work reference. It is not affiliated with or endorsed by Hubbell, the IBEW, or the IAM. Original documents remain the authority.",
        )
        ManualSection(
            "Getting started",
            "Enter your hire date, shift, current PTO and sick balances, call-ins remaining, and current attendance points. A reviewed attendance statement can reconcile the reported balance through its selected date. Setup remains editable under Settings. Home panels open the Calendar with the matching month and event type highlighted.",
        )
        ManualSection(
            "Attendance calculations",
            "The dashboard starts from the current point total you enter manually. Dated confirmed point entries identify individual 12-month falloffs; adding, editing, or removing past points in Settings keeps today's manual total unchanged. Pending, disputed, excused, and rescinded entries stay visible without changing calculations. Enter verified 90-day credits as credit events; estimated credit dates never create credits automatically.",
        )
        ManualSection(
            "Documents and printouts",
            "Tap Add document, choose its category, then take photos or choose files from your phone. Attendance intake accepts multiple pages by default and saves all pages from one scan or selection as a single document. Confirmed attendance-sheet rows become permanent dated records used by the Calendar and point-falloff calculations; they never set the current point total. Re-scanning the same rows skips duplicates and reports the result. Review detected rows before confirming them. Exception forms can propose booked vacation dates, and holiday calendars can propose dated plant holidays; review each result before saving. Saved documents can be opened in the portrait-aware zoomable viewer with OCR text.",
        )
        ManualSection(
            "PTO, sick time, holidays, and notes",
            "Opening balances come from setup. Use LOG to record full-day, half-day, or custom PTO, sick time, call-ins, floating holidays, and booked vacation. Sick time is shown as days; one sick day is eight hours. You receive five call-ins each calendar year. Logging one deducts eight PTO hours on first shift or ten on second shift. Approved bookings deduct their saved hours on their date; requested or cancelled bookings do not. Link actual usage or a call-in to its booking to avoid a second deduction. Existing booking durations do not change when you change shifts. Legacy bookings show assumed durations for review. Setup asks how many floating vacation days are available; those personal days are tracked separately and are never listed as dated plant holidays. Reviewed plant holidays also appear in Reference.",
        )
        ManualSection(
            "Calendar",
            "Calendar opens with all twelve months. A stronger red month indicator means more confirmed attendance points were accrued in that month; the printed point value remains authoritative. Tap a month to see its days. The pinned legend identifies green point falloffs, confirmed and estimated attendance credits, accrued points, call-ins, sick time, PTO, and plant holidays. Estimated credits use an outlined marker. Tap a legend item to filter, tap a day for full details, or log something with that date already selected. Attendance corrections recalculate contributions; removed attendance remains rescinded in history. Bookings can be approved or cancelled from day details. Manually entered points without dates cannot appear on the calendar; confirmed attendance-sheet rows do have dates and do appear.",
        )
        ManualSection(
            "Search",
            "Press the keyboard Search action or the search button. Reference searches bundled policies. Documents searches titles and indexed page text and opens the matching original page. Use Read / retry OCR to index a saved PDF or image collection. OCR continues in the background and can be cancelled or resumed. OCR results remain derived text and should be checked against the original.",
        )
        ManualSection(
            "Reminders and backup",
            "Settings can schedule a weekly check-in, enable app lock, correct balances, set current points and call-ins, manage past points individually, and export or import a ZIP containing structured records plus original documents. Format 7 offers Merge (skip identical records; stop on conflicts) or Replace (restore the snapshot). Files are validated before records change. Legacy formats 1–4 and 6 support additive import once per backup. Exported files are outside the app's protection, so store them securely. Delete all app data requires confirmation, clears all records and documents, and starts first-time setup again.",
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
        Text("Manual for Hub Helper ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ManualSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body)
    }
}
