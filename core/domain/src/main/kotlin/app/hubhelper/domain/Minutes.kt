package app.hubhelper.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Exact storage unit. Decimal hours are a presentation/input format only. */
@JvmInline
value class Minutes(val value: Long) {
    fun displayHours(): String = BigDecimal(value).divide(BigDecimal(60), 4, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

    companion object {
        fun fromHours(text: String): Minutes = Minutes(text.toBigDecimal().multiply(BigDecimal(60)).longValueExact())
    }
}

fun displayMinutesAsHours(minutes: Int): String = Minutes(minutes.toLong()).displayHours()
