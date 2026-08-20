package app.hubhelper.domain

import java.time.LocalDate
import java.time.Month

data class ParsedHoliday(val date: LocalDate, val name: String, val sourceLine: String)

data class ParsedHolidayCalendar(val holidays: List<ParsedHoliday>, val warnings: List<String>)

class HolidayCalendarParser {
    private val numericDate = Regex("""\b(\d{1,2})\s*[-/.]\s*(\d{1,2})(?:\s*[-/.]\s*(\d{2,4}))?\b""")
    private val namedDate = Regex(
        """\b(?:(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat|Sun)\.?\s*,?\s*)?(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, defaultYear: Int): ParsedHolidayCalendar {
        val lines = text.lineSequence().map { it.trim().replace(Regex("\\s+"), " ") }.filter(String::isNotBlank).toList()
        val holidays = lines.mapIndexedNotNull { index, line ->
            parseLine(line, defaultYear)?.let { (date, rawName) ->
                val nearbyName = rawName.takeIf { it.any(Char::isLetter) }
                    ?: lines.getOrNull(index + 1)?.takeIf(::looksLikeName)
                    ?: lines.getOrNull(index - 1)?.takeIf(::looksLikeName)
                ParsedHoliday(date, resolvedName(date, nearbyName), line)
            }
        }.filterNot { isFloatingHolidayName(it.name) }
            .distinctBy { it.date to it.name.lowercase() }
            .sortedBy { it.date }
        val warnings = buildList {
            if (holidays.isEmpty()) add("No holiday dates were recognized")
            if (holidays.any { it.name.startsWith("Holiday on ") }) add("Some holiday names were not recognized")
            if (holidays.any { it.date.year != defaultYear }) add("The calendar contains dates outside $defaultYear")
        }
        return ParsedHolidayCalendar(holidays, warnings)
    }

    private fun parseLine(line: String, defaultYear: Int): Pair<LocalDate, String>? {
        numericDate.find(line)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val rawYear = match.groupValues[3].toIntOrNull()
            val year = rawYear?.let { if (it < 100) 2000 + it else it } ?: defaultYear
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            return date to line.removeRange(match.range).trim()
        }
        namedDate.find(line)?.let { match ->
            val month = monthNumber(match.groupValues[1]) ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].toIntOrNull() ?: defaultYear
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
            return date to line.removeRange(match.range).trim()
        }
        return null
    }

    private fun monthNumber(value: String): Int? = Month.entries.firstOrNull {
        it.name.startsWith(value.take(3), ignoreCase = true)
    }?.value

    private fun looksLikeName(value: String): Boolean =
        value.any(Char::isLetter) && numericDate.find(value) == null && namedDate.find(value) == null && value.length <= 80

    private fun cleanName(value: String): String = value
        .trim(' ', '-', ':', '|', ',', '.')
        .replace(Regex("^(holiday|observed)\\s*[:|-]?\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
    
    fun resolvedName(date: LocalDate, candidate: String?): String {
        val cleaned = candidate?.let(::cleanName).orEmpty()
        inferredFixedHolidayName(date)?.let { return it }
        if (cleaned.equals("Last working day before Christmas", ignoreCase = true)) return "Christmas"
        val generic = cleaned.isBlank() || cleaned.matches(
            Regex("(?:annual\\s+)?(?:plant\\s+)?holiday(?:\\s+calendar)?", RegexOption.IGNORE_CASE),
        )
        return if (generic) inferredHolidayName(date) ?: "Holiday on ${date.monthValue}/${date.dayOfMonth}" else cleaned
    }

    fun isFloatingHolidayName(name: String): Boolean =
        name.contains("floating", ignoreCase = true) || name.contains("floater", ignoreCase = true)

    private fun inferredFixedHolidayName(date: LocalDate): String? = when {
        date.monthValue == 1 && date.dayOfMonth == 1 -> "New Year's Day"
        date.monthValue == 7 && date.dayOfMonth == 4 -> "Independence Day"
        date.monthValue == 7 && date.dayOfMonth == 3 && date.dayOfWeek.value == 5 && date.plusDays(1).dayOfMonth == 4 -> "Independence Day"
        date.monthValue == 7 && date.dayOfMonth == 5 && date.dayOfWeek.value == 1 && date.minusDays(1).dayOfMonth == 4 -> "Independence Day"
        date.monthValue == 12 && date.dayOfMonth == 25 -> "Christmas Day"
        else -> null
    }

    private fun inferredHolidayName(date: LocalDate): String? = when {
        inferredFixedHolidayName(date) != null -> inferredFixedHolidayName(date)
        date == easterSunday(date.year).minusDays(2) -> "Good Friday"
        date.monthValue == 5 && date.dayOfWeek.value == 1 && date.plusWeeks(1).monthValue == 6 -> "Memorial Day"
        date.monthValue == 9 && date.dayOfWeek.value == 1 && date.dayOfMonth <= 7 -> "Labor Day"
        date.monthValue == 11 && date.dayOfWeek.value == 4 && date.dayOfMonth in 22..28 -> "Thanksgiving Day"
        date.monthValue == 11 && date.dayOfWeek.value == 5 && date.minusDays(1).dayOfMonth in 22..28 -> "Friday after Thanksgiving"
        date.monthValue == 12 && date.dayOfMonth in 20..24 -> "Christmas"
        else -> null
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
