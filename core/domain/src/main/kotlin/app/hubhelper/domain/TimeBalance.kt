package app.hubhelper.domain

import java.time.LocalDate

enum class TimeBalanceKind { PTO, SICK, FLOATING_BIRTHDAY, FLOATING_ANYTIME }

data class TimeBalanceAdjustment(
    val id: String,
    val occurredOn: LocalDate,
    val kind: TimeBalanceKind,
    /** Positive adds time; negative records time used. */
    val minutes: Int,
    val note: String?,
)
