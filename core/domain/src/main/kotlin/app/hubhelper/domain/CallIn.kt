package app.hubhelper.domain

import java.time.LocalDate

const val ANNUAL_CALL_IN_ALLOWANCE = 5

enum class CallInColorBand { GREEN, ORANGE, RED }

data class CallInEvent(
    val id: String,
    val occurredOn: LocalDate,
    val ptoMinutes: Int,
    val bookingId: String? = null,
)

fun remainingCallIns(events: List<CallInEvent>, year: Int): Int =
    (ANNUAL_CALL_IN_ALLOWANCE - events.count { it.occurredOn.year == year }).coerceAtLeast(0)

fun callInColorBand(remaining: Int): CallInColorBand = when {
    remaining >= 4 -> CallInColorBand.GREEN
    remaining >= 2 -> CallInColorBand.ORANGE
    else -> CallInColorBand.RED
}
