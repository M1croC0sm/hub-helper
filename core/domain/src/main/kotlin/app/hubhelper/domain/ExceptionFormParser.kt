package app.hubhelper.domain

import java.time.LocalDate

data class ParsedExceptionForm(
    val bookedDates: List<LocalDate>,
    val warnings: List<String>,
)

class ExceptionFormParser {
    private val datePattern = Regex("""\b(\d{1,2})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{2,4})\b""")

    fun parse(text: String, today: LocalDate): ParsedExceptionForm {
        val dates = datePattern.findAll(text).mapNotNull { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val day = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val rawYear = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }.distinct().sorted().toList()
        val likelyVacationForm = listOf("vacation", "pto", "paid time", "exception")
            .any { text.contains(it, ignoreCase = true) }
        val warnings = buildList {
            if (dates.isEmpty()) add("No dates were recognized")
            if (!likelyVacationForm) add("Vacation or PTO wording was not recognized; verify every date before saving")
            if (dates.any { it.isBefore(today) }) add("The form includes one or more past dates")
        }
        return ParsedExceptionForm(dates, warnings)
    }
}
