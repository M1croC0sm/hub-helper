package app.hubhelper.domain

import java.time.LocalDate

enum class BookedTimeType { REGULAR_PTO, BIRTHDAY_FLOATING, ANYTIME_FLOATING }

data class BookedPtoDay(
    val id: String,
    val date: LocalDate,
    val sourceDocumentId: String? = null,
    val type: BookedTimeType = BookedTimeType.REGULAR_PTO,
)

fun nextBookedPto(days: List<BookedPtoDay>, asOf: LocalDate): BookedPtoDay? =
    days.filter { !it.date.isBefore(asOf) }.minByOrNull { it.date }

fun remainingFloatingHolidays(
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
    asOf: LocalDate,
    allowance: Int = 2,
): Int {
    val used = buildSet {
        adjustments.filter { it.occurredOn.year == asOf.year && !it.occurredOn.isAfter(asOf) && it.minutes < 0 }.forEach {
            when (it.kind) {
                TimeBalanceKind.FLOATING_BIRTHDAY -> add(BookedTimeType.BIRTHDAY_FLOATING)
                TimeBalanceKind.FLOATING_ANYTIME -> add(BookedTimeType.ANYTIME_FLOATING)
                else -> Unit
            }
        }
        bookedDays.filter { it.date.year == asOf.year }.forEach {
            if (it.type != BookedTimeType.REGULAR_PTO) add(it.type)
        }
    }
    return (allowance - used.size).coerceAtLeast(0)
}

fun floatingHolidayAvailable(
    type: BookedTimeType,
    adjustments: List<TimeBalanceAdjustment>,
    bookedDays: List<BookedPtoDay>,
    year: Int,
): Boolean {
    require(type != BookedTimeType.REGULAR_PTO)
    val recorded = adjustments.any {
        it.occurredOn.year == year && it.minutes < 0 &&
            ((type == BookedTimeType.BIRTHDAY_FLOATING && it.kind == TimeBalanceKind.FLOATING_BIRTHDAY) ||
                (type == BookedTimeType.ANYTIME_FLOATING && it.kind == TimeBalanceKind.FLOATING_ANYTIME))
    }
    return !recorded && bookedDays.none { it.date.year == year && it.type == type }
}
