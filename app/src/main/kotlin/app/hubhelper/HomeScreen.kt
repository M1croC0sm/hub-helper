package app.hubhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.TimeOffCalculator
import java.math.BigDecimal
import java.time.LocalDate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalDensity
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(
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
    paydays: List<LocalDate>,
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
    val total = BigDecimal(AttendanceCalculator().totalWithOpening(events, appDate, app.hubhelper.domain.HalfPoints(opening.multiply(BigDecimal(2)).intValueExact())).value).divide(BigDecimal(2))
    val currentTotal = total.stripTrailingZeros().toPlainString()
    fun balance(kind: app.hubhelper.domain.TimeBalanceKind, openingHours: String, date: LocalDate = appDate): String {
        return TimeOffCalculator.balanceHours(
            kind = kind,
            enteredBalanceHours = openingHours,
            enteredBalanceDate = runCatching { LocalDate.parse(setupData.balancesAsOfDate) }.getOrDefault(appDate),
            hireDate = runCatching { LocalDate.parse(setupData.hireDate) }.getOrNull(),
            asOf = date,
            adjustments = timeAdjustments,
            callIns = callIns,
            bookedPtoDays = bookedPtoDays,
            regularBookedPtoMinutes = if (setupData.shiftPreset == "SECOND") 10 * 60 else 8 * 60,
        )
    }
    val ptoBalance = balance(app.hubhelper.domain.TimeBalanceKind.PTO, setupData.ptoBalanceHours)
    val sickBalance = balance(app.hubhelper.domain.TimeBalanceKind.SICK, setupData.sickBalanceHours)
    val nextHoliday = holidays.firstOrNull { !it.date.isBefore(appDate) }
    val nextBookedTimeOff = app.hubhelper.domain.nextApprovedTimeOff(bookedPtoDays, appDate)
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
        val upcomingPto = app.hubhelper.domain.upcomingApprovedPto(
            bookedPtoDays,
            appDate,
            defaultDurationMinutes = ptoWarningAt * 60,
        )
        val afterBookingsBalance = upcomingPto.throughDate?.let {
            balance(app.hubhelper.domain.TimeBalanceKind.PTO, setupData.ptoBalanceHours, it)
        } ?: ptoBalance
        TimeOffPanel(
            ptoBalance = ptoBalance,
            afterBookingsBalance = afterBookingsBalance,
            upcomingPto = upcomingPto,
            floatingRemaining = floatingRemaining,
            sickBalance = sickBalance,
            ptoColor = ptoColor,
            sickColor = design.sick,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.PTO)) },
        )
        val nextPayday = paydays.firstOrNull { !it.isBefore(appDate) }
        if (!largeFont && nextPayday != null) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(design.contentSpacing)) {
                CallInPanel(callInsRemaining, Modifier.weight(1f).fillMaxHeight()) {
                    onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.CALL_IN))
                }
                val paydayDaysAway = ChronoUnit.DAYS.between(appDate, nextPayday).coerceAtLeast(0)
                val paydayProgress = (1f - paydayDaysAway.toFloat() / 14f).coerceIn(0f, 1f)
                val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val faded = if (darkSurface) Color(0xFF555A60) else Color(0xFFB7B7B7)
                val bright = if (darkSurface) Color.White else Color(0xFF151515)
                val paydayColor = if (nextPayday == appDate) design.good else lerp(faded, bright, paydayProgress)
                HubPanel(Modifier.weight(1f).fillMaxHeight().clickable { onViewCalendar(CalendarRequest(YearMonth.from(nextPayday))) }, accent = paydayColor) {
                    SectionLabel("Next payday", color = paydayColor)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(nextPayday.format(DateTimeFormatter.ofPattern("MMM d, yy")), style = MaterialTheme.typography.titleLarge, color = paydayColor, fontWeight = FontWeight.Bold)
                        Text("FRIDAY", style = MaterialTheme.typography.labelSmall, color = paydayColor)
                    }
                }
            }
        } else {
            CallInPanel(callInsRemaining, Modifier.fillMaxWidth()) { onViewCalendar(CalendarRequest(YearMonth.from(appDate), CalendarFilter.CALL_IN)) }
            nextPayday?.let { date ->
                val paydayDaysAway = ChronoUnit.DAYS.between(appDate, date).coerceAtLeast(0)
                val paydayProgress = (1f - paydayDaysAway.toFloat() / 14f).coerceIn(0f, 1f)
                val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val faded = if (darkSurface) Color(0xFF555A60) else Color(0xFFB7B7B7)
                val bright = if (darkSurface) Color.White else Color(0xFF151515)
                val paydayColor = if (date == appDate) design.good else lerp(faded, bright, paydayProgress)
                HubPanel(Modifier.fillMaxWidth().clickable { onViewCalendar(CalendarRequest(YearMonth.from(date))) }, accent = paydayColor) {
                    SectionLabel("Next payday", color = paydayColor)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("MMM d, yy")), style = MaterialTheme.typography.titleLarge, color = paydayColor, fontWeight = FontWeight.Bold)
                        Text("FRIDAY", style = MaterialTheme.typography.labelSmall, color = paydayColor)
                    }
                }
            }
        }

        HubPanel(
            Modifier
                .fillMaxWidth()
                .clickable {
                    val nextDate = listOfNotNull(nextBookedTimeOff?.date, nextHoliday?.date).minOrNull() ?: appDate
                    onViewCalendar(
                        CalendarRequest(YearMonth.from(nextDate), CalendarFilter.ALL),
                    )
                },
        ) {
            SectionLabel("Next time off")
            Spacer(Modifier.height(8.dp))
            SectionLabel("Booked time off", color = design.attention)
            if (nextBookedTimeOff == null) {
                Text("No approved time off booked", style = MaterialTheme.typography.titleMedium)
            } else {
                Text(nextBookedTimeOff.date.monthDayYear(), style = MaterialTheme.typography.titleLarge, color = design.attention)
                Text(
                    when (nextBookedTimeOff.type) {
                        app.hubhelper.domain.BookedTimeType.REGULAR_PTO -> {
                            val minutes = nextBookedTimeOff.durationMinutes.takeIf { it > 0 } ?: ptoWarningAt * 60
                            "${app.hubhelper.domain.Minutes(minutes.toLong()).displayHours()} PTO hours"
                        }
                        app.hubhelper.domain.BookedTimeType.BIRTHDAY_FLOATING -> "Birthday floating holiday"
                        app.hubhelper.domain.BookedTimeType.ANYTIME_FLOATING -> "Anytime floating holiday"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(13.dp))
            SectionLabel("Plant holiday", color = design.attention)
            if (nextHoliday == null) {
                Text("No reviewed holiday loaded", style = MaterialTheme.typography.titleMedium)
                Text("Add the annual plant calendar in Documents.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nextHoliday.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                    Text(nextHoliday.date.monthDayYear(), style = MaterialTheme.typography.titleLarge, color = design.attention)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("OPEN CALENDAR  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                ActivityRow(adjustment.occurredOn, adjustment.kind.name, "${app.hubhelper.domain.displayMinutesAsHours(adjustment.minutes)} h", "user")
            }
            callInRecent.forEach { event -> ActivityRow(event.occurredOn, "call-in day", "−${event.ptoMinutes / 60} h PTO", "excused") }
            noteRecent.forEach { note -> ActivityRow(note.date, "work note", "", "user") }
            Text("OPEN CALENDAR  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("Unofficial employee reference • Original records remain authoritative", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

}

@Composable
internal fun CallInPanel(
    remaining: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = when (app.hubhelper.domain.callInColorBand(remaining)) {
        app.hubhelper.domain.CallInColorBand.GREEN -> HubThemeDesign.tokens.good
        app.hubhelper.domain.CallInColorBand.ORANGE -> HubThemeDesign.tokens.attention
        app.hubhelper.domain.CallInColorBand.RED -> MaterialTheme.colorScheme.error
    }
    HubPanel(modifier.clickable(onClick = onClick), accent = color) {
        SectionLabel("Call-ins", color = color)
        MetricValue(remaining.toString(), "LEFT", color)
    }
}

@Composable
internal fun TimeOffPanel(
    ptoBalance: String,
    afterBookingsBalance: String,
    upcomingPto: app.hubhelper.domain.UpcomingPtoSummary,
    floatingRemaining: Int,
    sickBalance: String,
    ptoColor: Color,
    sickColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    HubPanel(modifier.clickable(onClick = onClick), accent = ptoColor) {
        SectionLabel("Time off")
        Spacer(Modifier.height(8.dp))
        SectionLabel("PTO", color = ptoColor)
        MetricValue(ptoBalance, "HRS", ptoColor)
        Text("Available today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(13.dp))
        val afterBookings: @Composable (Modifier) -> Unit = { itemModifier ->
            Column(itemModifier) {
                SectionLabel("After bookings", color = ptoColor)
                MetricValue(afterBookingsBalance, "HRS", ptoColor)
            }
        }
        val upcomingBooked: @Composable (Modifier) -> Unit = { itemModifier ->
            Column(itemModifier) {
                SectionLabel("Upcoming booked")
                MetricValue(upcomingPto.minutes.displayHours(), "HRS")
                Text(
                    when (upcomingPto.bookingCount) {
                        0 -> "No approved days"
                        1 -> "1 approved day"
                        else -> "${upcomingPto.bookingCount} approved days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (largeFont) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                afterBookings(Modifier.fillMaxWidth())
                upcomingBooked(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                afterBookings(Modifier.weight(1f))
                upcomingBooked(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(13.dp))
        val floating: @Composable (Modifier) -> Unit = { itemModifier ->
            Column(itemModifier) {
                SectionLabel("Floating holidays", color = ptoColor)
                MetricValue(floatingRemaining.toString(), "LEFT", ptoColor)
            }
        }
        val sick: @Composable (Modifier) -> Unit = { itemModifier ->
            Column(itemModifier) {
                SectionLabel("Sick time", color = sickColor)
                MetricValue(sickBalance, "HRS", sickColor)
                Text("Left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (largeFont) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                floating(Modifier.fillMaxWidth())
                sick(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                floating(Modifier.weight(1f))
                sick(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("OPEN PTO CALENDAR  ›", style = MaterialTheme.typography.labelLarge, color = ptoColor)
    }
}

@Composable
internal fun ActivityRow(date: LocalDate, label: String, change: String, status: String) {
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
