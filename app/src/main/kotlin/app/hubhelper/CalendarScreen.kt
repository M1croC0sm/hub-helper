package app.hubhelper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hubhelper.domain.AttendanceCalculator
import app.hubhelper.domain.AttendanceEvent
import app.hubhelper.domain.AttendanceEventStatus
import app.hubhelper.domain.AttendanceEventType
import app.hubhelper.domain.BookedPtoDay
import app.hubhelper.domain.BookedTimeType
import app.hubhelper.domain.CallInEvent
import app.hubhelper.domain.PlantHoliday
import app.hubhelper.domain.TimeBalanceAdjustment
import app.hubhelper.domain.TimeBalanceKind
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class CalendarFilter(val label: String) {
    ALL("All"),
    ATTENDANCE("Attendance"),
    PTO("PTO"),
    SICK("Sick"),
    CALL_IN("Call-in"),
    HOLIDAY("Holiday"),
}

data class CalendarRequest(
    val month: YearMonth? = null,
    val filter: CalendarFilter = CalendarFilter.ALL,
)

private enum class MarkerType(
    val shortLabel: String,
    val symbol: String,
    val filter: CalendarFilter,
) {
    POINT_FALLOFF("Falloff", "+", CalendarFilter.ATTENDANCE),
    CREDIT_CONFIRMED("Credit", "+", CalendarFilter.ATTENDANCE),
    CREDIT_ESTIMATED("Est. credit", "+", CalendarFilter.ATTENDANCE),
    POINT_ACCRUED("Point", "−", CalendarFilter.ATTENDANCE),
    CALL_IN("Call-in", "−", CalendarFilter.CALL_IN),
    SICK("Sick", "−", CalendarFilter.SICK),
    PTO("PTO", "☺", CalendarFilter.PTO),
    HOLIDAY("Holiday", "★", CalendarFilter.HOLIDAY),
    CORRECTION("Correction", "±", CalendarFilter.ALL),
}

private sealed interface MarkerSource {
    data class Attendance(val event: AttendanceEvent) : MarkerSource
    data class Time(val adjustment: TimeBalanceAdjustment) : MarkerSource
    data class CallIn(val event: CallInEvent) : MarkerSource
    data class BookedPto(val day: BookedPtoDay) : MarkerSource
    data class Holiday(val holiday: PlantHoliday) : MarkerSource
    data object Derived : MarkerSource
}

private data class CalendarMarker(
    val id: String,
    val date: LocalDate,
    val type: MarkerType,
    val title: String,
    val detail: String,
    val source: MarkerSource,
    val confirmed: Boolean = true,
)

private data class LegendEntry(val type: MarkerType, val label: String)

private val legendEntries = listOf(
    LegendEntry(MarkerType.POINT_FALLOFF, "Falloff"),
    LegendEntry(MarkerType.CREDIT_CONFIRMED, "Credit"),
    LegendEntry(MarkerType.POINT_ACCRUED, "Point"),
    LegendEntry(MarkerType.CALL_IN, "Call-in"),
    LegendEntry(MarkerType.SICK, "Sick"),
    LegendEntry(MarkerType.PTO, "PTO"),
    LegendEntry(MarkerType.HOLIDAY, "Holiday"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    padding: PaddingValues,
    appDate: LocalDate,
    openingBalance: String,
    events: List<AttendanceEvent>,
    timeAdjustments: List<TimeBalanceAdjustment>,
    callIns: List<CallInEvent>,
    bookedPtoDays: List<BookedPtoDay>,
    holidays: List<PlantHoliday>,
    request: CalendarRequest,
    onLogDate: (LocalDate) -> Unit,
    onUpdateAttendance: (AttendanceEvent) -> Unit,
    onDeleteAttendance: (AttendanceEvent) -> Unit,
    onDeleteTimeAdjustment: (TimeBalanceAdjustment) -> Unit,
    onDeleteCallIn: (CallInEvent) -> Unit,
    onDeleteBookedPto: (BookedPtoDay) -> Unit,
    onDeleteHoliday: (PlantHoliday) -> Unit,
) {
    var visibleYear by remember(request) { mutableIntStateOf(request.month?.year ?: appDate.year) }
    var selectedMonth by remember(request) { mutableStateOf(request.month) }
    var filter by remember(request) { mutableStateOf(request.filter) }
    var selectedDay by remember(request) { mutableStateOf<LocalDate?>(null) }
    var editAttendance by remember { mutableStateOf<AttendanceEvent?>(null) }
    var deleteMarker by remember { mutableStateOf<CalendarMarker?>(null) }
    val allMarkers = remember(events, timeAdjustments, callIns, bookedPtoDays, holidays, appDate) {
        buildCalendarMarkers(events, timeAdjustments, callIns, bookedPtoDays, holidays, appDate)
    }
    val filteredMarkers = remember(allMarkers, filter) {
        if (filter == CalendarFilter.ALL) allMarkers
        else allMarkers.filter { it.type.filter == filter }
    }

    Column(
        modifier = Modifier.padding(padding),
    ) {
        CalendarHeader(
            visibleYear = visibleYear,
            selectedMonth = selectedMonth,
            onPrevious = {
                if (selectedMonth == null) visibleYear--
                else selectedMonth = selectedMonth!!.minusMonths(1).also { visibleYear = it.year }
            },
            onNext = {
                if (selectedMonth == null) visibleYear++
                else selectedMonth = selectedMonth!!.plusMonths(1).also { visibleYear = it.year }
            },
            onYear = { selectedMonth = null },
            onToday = {
                visibleYear = appDate.year
                selectedMonth = YearMonth.from(appDate)
            },
        )
        CalendarLegend(
            selectedFilter = filter,
            onSelect = { chosen -> filter = if (filter == chosen) CalendarFilter.ALL else chosen },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HubThemeDesign.tokens.screenPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(HubThemeDesign.tokens.contentSpacing),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
            }

            if ((openingBalance.toBigDecimalOrNull() ?: BigDecimal.ZERO).signum() != 0) {
                HubPanel(Modifier.fillMaxWidth(), accent = HubThemeDesign.tokens.attention) {
                    Text("Dated activity only", fontWeight = FontWeight.SemiBold)
                    Text(
                        "The current total includes ${openingBalance.ifBlank { "0" }} manually entered points without individual dates, so those points cannot appear on the calendar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (selectedMonth == null) {
                YearOverview(
                    year = visibleYear,
                    allMarkers = allMarkers,
                    visibleMarkers = filteredMarkers,
                    onMonth = { selectedMonth = it },
                )
            } else {
                MonthOverview(
                    month = selectedMonth!!,
                    markers = filteredMarkers,
                    appDate = appDate,
                    onDay = { selectedDay = it },
                )
            }
        }
    }

    selectedDay?.let { day ->
        val dayMarkers = filteredMarkers.filter { it.date == day }.sortedBy { markerPriority(it.type) }
        ModalBottomSheet(onDismissRequest = { selectedDay = null; editAttendance = null }) {
            DayDetails(
                day = day,
                markers = dayMarkers,
                editing = editAttendance,
                onEdit = { editAttendance = it },
                onSaveAttendance = { updated ->
                    onUpdateAttendance(updated)
                    editAttendance = null
                },
                onDelete = { deleteMarker = it },
                onLog = {
                    selectedDay = null
                    onLogDate(day)
                },
            )
        }
    }

    deleteMarker?.let { marker ->
        AlertDialog(
            onDismissRequest = { deleteMarker = null },
            title = { Text("Remove calendar entry?") },
            text = { Text("Remove ${marker.title} from ${marker.date.monthDayYear()}? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    when (val source = marker.source) {
                        is MarkerSource.Attendance -> onDeleteAttendance(source.event)
                        is MarkerSource.Time -> onDeleteTimeAdjustment(source.adjustment)
                        is MarkerSource.CallIn -> onDeleteCallIn(source.event)
                        is MarkerSource.BookedPto -> onDeleteBookedPto(source.day)
                        is MarkerSource.Holiday -> onDeleteHoliday(source.holiday)
                        MarkerSource.Derived -> Unit
                    }
                    deleteMarker = null
                }) { Text("REMOVE") }
            },
            dismissButton = { TextButton(onClick = { deleteMarker = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CalendarHeader(
    visibleYear: Int,
    selectedMonth: YearMonth?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onYear: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Previous ${if (selectedMonth == null) "year" else "month"}" }) { Text("‹") }
        TextButton(onClick = onYear, modifier = Modifier.weight(1f)) {
            Text(
                selectedMonth?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: visibleYear.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (selectedMonth != null) TextButton(onClick = onYear) { Text("Year") }
        TextButton(onClick = onToday) { Text("Today") }
        TextButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = "Next ${if (selectedMonth == null) "year" else "month"}" }) { Text("›") }
    }
}

@Composable
private fun CalendarLegend(selectedFilter: CalendarFilter, onSelect: (CalendarFilter) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        legendEntries.chunked(4).forEach { entries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                entries.forEach { entry ->
                    val selected = selectedFilter == entry.type.filter
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(entry.type.filter) }
                            .semantics { contentDescription = "${entry.label} calendar filter" },
                        shape = HubThemeDesign.tokens.badgeShape,
                        color = if (selected) markerColor(entry.type).copy(alpha = 0.18f) else Color.Transparent,
                        border = BorderStroke(1.dp, markerColor(entry.type).copy(alpha = if (selected) 0.9f else 0.4f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MarkerSymbol(entry.type, compact = true)
                            Text(" ${entry.label}", style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
                repeat(4 - entries.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun YearOverview(
    year: Int,
    allMarkers: List<CalendarMarker>,
    visibleMarkers: List<CalendarMarker>,
    onMonth: (YearMonth) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..12).chunked(2).forEach { rowMonths ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMonths.forEach { monthNumber ->
                    val month = YearMonth.of(year, monthNumber)
                    val confirmedAccrued = allMarkers.filter {
                        YearMonth.from(it.date) == month && it.type == MarkerType.POINT_ACCRUED && it.confirmed
                    }.sumOf { markerPointsHalf(it) }
                    val count = visibleMarkers.count { YearMonth.from(it.date) == month }
                    MonthCell(month, confirmedAccrued, count, Modifier.weight(1f)) { onMonth(month) }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    month: YearMonth,
    accruedHalfPoints: Int,
    visibleEventCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val heat = calendarMonthHeatLevel(accruedHalfPoints)
    val heatAlpha = listOf(0f, 0.08f, 0.14f, 0.21f, 0.29f)[heat]
    val points = BigDecimal(accruedHalfPoints).divide(BigDecimal(2)).stripTrailingZeros().toPlainString()
    HubPanel(
        modifier.clickable(onClick = onClick).semantics {
            contentDescription = "${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}, $points attendance points accrued, $visibleEventCount visible calendar entries"
        },
        accent = if (heat == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
    ) {
        if (heat > 0) {
            Surface(
                Modifier.fillMaxWidth().heightIn(min = 7.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = heatAlpha.coerceAtLeast(0.10f)),
                shape = HubThemeDesign.tokens.badgeShape,
            ) {}
        }
        Text(month.format(DateTimeFormatter.ofPattern("MMM")).uppercase(), style = MaterialTheme.typography.titleLarge)
        Text("$points PTS ACCRUED", style = MaterialTheme.typography.labelMedium, color = if (heat > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$visibleEventCount ${if (visibleEventCount == 1) "entry" else "entries"}", style = MaterialTheme.typography.bodySmall)
    }
}

internal fun calendarMonthHeatLevel(accruedHalfPoints: Int): Int = when {
    accruedHalfPoints <= 0 -> 0
    accruedHalfPoints == 1 -> 1
    accruedHalfPoints == 2 -> 2
    accruedHalfPoints == 3 -> 3
    else -> 4
}

@Composable
private fun MonthOverview(month: YearMonth, markers: List<CalendarMarker>, appDate: LocalDate, onDay: (LocalDate) -> Unit) {
    val firstOffset = month.atDay(1).dayOfWeek.sundayIndex()
    val cells = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (week + List(7 - week.size) { null }).forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).heightIn(min = 70.dp))
                    else DayCell(
                        day = day,
                        markers = markers.filter { it.date == day }.sortedBy { markerPriority(it.type) },
                        isToday = day == appDate,
                        modifier = Modifier.weight(1f),
                        onClick = { onDay(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, markers: List<CalendarMarker>, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .heightIn(min = 70.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = dayDescription(day, markers) },
        shape = HubThemeDesign.tokens.badgeShape,
        color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(if (isToday) 2.dp else 1.dp, if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                markers.take(3).forEach { MarkerSymbol(it.type, compact = true, confirmed = it.confirmed) }
            }
            if (markers.size > 3) Text("+${markers.size - 3}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MarkerSymbol(type: MarkerType, compact: Boolean, confirmed: Boolean = true) {
    Surface(
        modifier = Modifier.size(if (compact) 18.dp else 30.dp),
        shape = CircleShape,
        color = if (type == MarkerType.CREDIT_ESTIMATED || !confirmed) Color.Transparent else markerColor(type).copy(alpha = 0.18f),
        border = BorderStroke(1.dp, markerColor(type).copy(alpha = if (confirmed) 0.95f else 0.55f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(type.symbol, color = markerColor(type), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun markerColor(type: MarkerType): Color = when (type) {
    MarkerType.POINT_FALLOFF -> HubThemeDesign.tokens.good
    MarkerType.CREDIT_CONFIRMED, MarkerType.CREDIT_ESTIMATED, MarkerType.CALL_IN, MarkerType.HOLIDAY -> HubThemeDesign.tokens.attention
    MarkerType.POINT_ACCRUED -> MaterialTheme.colorScheme.error
    MarkerType.SICK -> MaterialTheme.colorScheme.onSurface
    MarkerType.PTO -> HubThemeDesign.tokens.good
    MarkerType.CORRECTION -> HubThemeDesign.tokens.pto
}

@Composable
private fun DayDetails(
    day: LocalDate,
    markers: List<CalendarMarker>,
    editing: AttendanceEvent?,
    onEdit: (AttendanceEvent?) -> Unit,
    onSaveAttendance: (AttendanceEvent) -> Unit,
    onDelete: (CalendarMarker) -> Unit,
    onLog: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = HubThemeDesign.tokens.screenPadding).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), style = MaterialTheme.typography.titleLarge)
        if (editing != null) {
            AttendanceEventForm(editing.occurredOn, editing) { date, type, points, status, note ->
                onSaveAttendance(editing.copy(occurredOn = date, type = type, points = points, status = status, note = note))
            }
            TextButton(onClick = { onEdit(null) }) { Text("Cancel editing") }
        } else {
            if (markers.isEmpty()) Text("No calendar entries for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            markers.forEach { marker ->
                HubPanel(Modifier.fillMaxWidth(), accent = markerColor(marker.type)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MarkerSymbol(marker.type, compact = false, confirmed = marker.confirmed)
                        Column(Modifier.weight(1f)) {
                            Text(marker.title, fontWeight = FontWeight.SemiBold)
                            Text(marker.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    when (val source = marker.source) {
                        is MarkerSource.Attendance -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(source.event) }) { Text("Edit") }
                            OutlinedButton(onClick = { onDelete(marker) }) { Text("Remove") }
                        }
                        MarkerSource.Derived -> Text("Calculated from dated attendance history", style = MaterialTheme.typography.bodySmall)
                        else -> OutlinedButton(onClick = { onDelete(marker) }) { Text("Remove") }
                    }
                }
            }
            Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) { Text("LOG SOMETHING ON THIS DATE") }
        }
    }
}

private fun buildCalendarMarkers(
    events: List<AttendanceEvent>,
    adjustments: List<TimeBalanceAdjustment>,
    callIns: List<CallInEvent>,
    bookedPto: List<BookedPtoDay>,
    holidays: List<PlantHoliday>,
    appDate: LocalDate,
): List<CalendarMarker> {
    val calculator = AttendanceCalculator()
    val markers = mutableListOf<CalendarMarker>()
    events.forEach { event ->
        if (event.type == AttendanceEventType.ATTENDANCE_CREDIT) {
            markers += CalendarMarker(
                "attendance-${event.id}", event.occurredOn, MarkerType.CREDIT_CONFIRMED,
                "90-day attendance credit", "+${event.points.asDisplayValue()} point credit • ${event.status.displayName()}",
                MarkerSource.Attendance(event), confirmed = event.status == AttendanceEventStatus.CONFIRMED,
            )
        } else {
            markers += CalendarMarker(
                "attendance-${event.id}", event.occurredOn, MarkerType.POINT_ACCRUED,
                attendanceTypeLabel(event.type), "+${event.points.asDisplayValue()} point${if (event.points.value == 2) "s" else ""} accrued • ${event.status.displayName()}",
                MarkerSource.Attendance(event), confirmed = event.status == AttendanceEventStatus.CONFIRMED,
            )
            if (event.status == AttendanceEventStatus.CONFIRMED) {
                markers += CalendarMarker(
                    "falloff-${event.id}", calculator.expiresOn(event), MarkerType.POINT_FALLOFF,
                    "Point falloff", "+${event.points.asDisplayValue()} points • from ${event.occurredOn.monthDayYear()}",
                    MarkerSource.Derived,
                )
            }
        }
    }
    calculator.nextAttendanceCreditDate(events, appDate)?.let { estimated ->
        val confirmedOnDate = events.any { it.occurredOn == estimated && it.type == AttendanceEventType.ATTENDANCE_CREDIT && it.status == AttendanceEventStatus.CONFIRMED }
        if (!confirmedOnDate) markers += CalendarMarker(
            "estimated-credit-$estimated", estimated, MarkerType.CREDIT_ESTIMATED,
            "Estimated 90-day credit", "Projected date; not yet confirmed", MarkerSource.Derived, confirmed = false,
        )
    }
    adjustments.forEach { adjustment ->
        val used = adjustment.minutes < 0
        val type = when {
            !used -> MarkerType.CORRECTION
            adjustment.kind == TimeBalanceKind.SICK -> MarkerType.SICK
            else -> MarkerType.PTO
        }
        val amount = formatMinutes(abs(adjustment.minutes), adjustment.kind == TimeBalanceKind.SICK)
        markers += CalendarMarker(
            "time-${adjustment.id}", adjustment.occurredOn, type,
            when (type) {
                MarkerType.SICK -> "Sick time used"
                MarkerType.PTO -> if (adjustment.kind == TimeBalanceKind.PTO) "PTO used" else "Floating holiday used"
                else -> "Balance correction"
            },
            "${if (used) "−" else "+"}$amount${adjustment.note?.let { " • $it" }.orEmpty()}",
            MarkerSource.Time(adjustment),
        )
    }
    callIns.forEach { event ->
        markers += CalendarMarker(
            "callin-${event.id}", event.occurredOn, MarkerType.CALL_IN,
            "Call-in used", "−1 call-in • −${event.ptoMinutes / 60} PTO hours", MarkerSource.CallIn(event),
        )
    }
    bookedPto.forEach { day ->
        markers += CalendarMarker(
            "booked-${day.id}", day.date, MarkerType.PTO,
            when (day.type) {
                BookedTimeType.REGULAR_PTO -> "PTO booked"
                BookedTimeType.BIRTHDAY_FLOATING -> "Birthday floating holiday booked"
                BookedTimeType.ANYTIME_FLOATING -> "Anytime floating holiday booked"
            },
            if (day.sourceDocumentId == null) "Added by user" else "Linked to imported source",
            MarkerSource.BookedPto(day),
        )
    }
    holidays.forEach { holiday ->
        markers += CalendarMarker(
            "holiday-${holiday.id}", holiday.date, MarkerType.HOLIDAY,
            holiday.name, "Plant holiday", MarkerSource.Holiday(holiday),
        )
    }
    return markers.distinctBy { it.id }.sortedWith(compareBy({ it.date }, { markerPriority(it.type) }))
}

private fun markerPriority(type: MarkerType): Int = when (type) {
    MarkerType.POINT_ACCRUED -> 0
    MarkerType.POINT_FALLOFF -> 1
    MarkerType.CREDIT_CONFIRMED -> 2
    MarkerType.CREDIT_ESTIMATED -> 3
    MarkerType.CALL_IN -> 4
    MarkerType.SICK -> 5
    MarkerType.PTO -> 6
    MarkerType.HOLIDAY -> 7
    MarkerType.CORRECTION -> 8
}

private fun markerPointsHalf(marker: CalendarMarker): Int =
    ((marker.source as? MarkerSource.Attendance)?.event?.points?.value ?: 0)

private fun dayDescription(day: LocalDate, markers: List<CalendarMarker>): String = buildString {
    append(day.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")))
    if (markers.isEmpty()) append(", no entries")
    else append(markers.joinToString(prefix = ", ") { it.title })
}

private fun DayOfWeek.sundayIndex(): Int = when (this) {
    DayOfWeek.SUNDAY -> 0
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
}

private fun AttendanceEventStatus.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun attendanceTypeLabel(type: AttendanceEventType): String = when (type) {
    AttendanceEventType.UNEXCUSED_ABSENCE -> "Absence"
    AttendanceEventType.TARDY -> "Tardy"
    AttendanceEventType.LEFT_EARLY -> "Left early"
    AttendanceEventType.CALL_IN_VIOLATION -> "Call-in violation"
    AttendanceEventType.ATTENDANCE_CREDIT -> "Attendance credit"
}

private fun formatMinutes(minutes: Int, sick: Boolean): String {
    val hours = BigDecimal(minutes).divide(BigDecimal(60)).stripTrailingZeros().toPlainString()
    if (!sick) return "$hours hours"
    val days = BigDecimal(minutes).divide(BigDecimal(480)).stripTrailingZeros().toPlainString()
    return "$days ${if (days == "1") "sick day" else "sick days"}"
}
