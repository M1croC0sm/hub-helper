package app.hubhelper.domain

import java.time.LocalDate

const val ANNUAL_CALL_IN_ALLOWANCE = 5

data class CallInEvent(
    val id: String,
    val occurredOn: LocalDate,
    val ptoMinutes: Int,
)

fun remainingCallIns(events: List<CallInEvent>, year: Int): Int =
    (ANNUAL_CALL_IN_ALLOWANCE - events.count { it.occurredOn.year == year }).coerceAtLeast(0)
