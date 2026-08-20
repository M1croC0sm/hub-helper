package app.hubhelper.domain

import java.time.LocalDate

data class ParsedAttendanceRow(
    val date: LocalDate,
    val comment: String,
    val adjustmentHalfPoints: Int?,
    val runningTotalHalfPoints: Int,
)

data class ParsedAttendanceStatement(
    val rows: List<ParsedAttendanceRow>,
    val currentTotalHalfPoints: Int?,
    val warnings: List<String>,
)

/** Parses both row-oriented OCR and OCR that reads table columns separately. */
class AttendancePrintoutParser {
    private val datePattern = Regex("""(\d{1,2}\s*[-/.]\s*\d{1,2}\s*[-/.]\s*\d{2,4})""")
    private val number = Regex("""(?<![\d.-])-?(?:\d+(?:\.\d+)?|\.\d+)""")
    private val standaloneNumber = Regex("""^\s*-?(?:\d+(?:\.\d+)?|\.\d+)\s*$""")
    private val pointAdjustment = Regex(
        """(-?(?:\d+(?:\.\d+)?|\.\d+))\s*(?:pt|pts|point|points)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): ParsedAttendanceStatement {
        val warnings = mutableListOf<String>()
        val historyStart = text.indexOf("Point History", ignoreCase = true)
        val historyText = if (historyStart >= 0) text.substring(historyStart + "Point History".length) else text
        val lines = historyText.lineSequence().map(::normalize).filter(String::isNotBlank).toList()
        val inlineRows = lines.mapNotNull(::parseInlineRow)
        val allDates = lines.mapNotNull { line -> datePattern.find(line)?.groupValues?.get(1)?.let(::parseDate) }
        val standaloneTotals = lines.mapNotNull { line ->
            line.takeIf { standaloneNumber.matches(it) }?.let(::toHalfPoints)?.takeIf { it in -2..16 }
        }

        val rows = if (inlineRows.size >= allDates.size.coerceAtMost(2)) {
            inferMissingAdjustments(inlineRows)
        } else {
            parseSeparatedColumns(lines, allDates, standaloneTotals, warnings)
        }
        if (rows.isEmpty()) warnings += "No complete dated rows were recognized"
        val currentTotal = standaloneTotals.lastOrNull() ?: rows.lastOrNull()?.runningTotalHalfPoints
        if (rows.any { it.adjustmentHalfPoints == null }) warnings += "Some row changes need manual review"
        return ParsedAttendanceStatement(rows, currentTotal, warnings.distinct())
    }

    private fun parseInlineRow(line: String): ParsedAttendanceRow? {
        val match = datePattern.find(line) ?: return null
        val date = parseDate(match.groupValues[1]) ?: return null
        val remainder = line.substring(match.range.last + 1).trim().trim('|')
        val numericMatches = number.findAll(remainder).toList()
        if (numericMatches.isEmpty()) return null
        val runningTotal = toHalfPoints(numericMatches.last().value) ?: return null
        val adjustment = pointAdjustment.find(remainder)?.groupValues?.get(1)?.let(::toHalfPoints)
        val comment = remainder.removeRange(numericMatches.last().range).trim().trimEnd('|')
        return ParsedAttendanceRow(date, comment, adjustment, runningTotal)
    }

    private fun parseSeparatedColumns(
        lines: List<String>,
        dates: List<LocalDate>,
        totals: List<Int>,
        warnings: MutableList<String>,
    ): List<ParsedAttendanceRow> {
        if (dates.isEmpty() || totals.size < dates.size) return emptyList()
        val comments = lines.filter { line ->
            datePattern.find(line) == null && !standaloneNumber.matches(line) &&
                listOf("pt", "absent", "tardy", "late", "call", "roll", "early", "sick")
                    .any { marker -> line.contains(marker, ignoreCase = true) }
        }
        if (comments.size < dates.size) warnings += "Some comments could not be matched to their dates"
        val rawRows = dates.indices.map { index ->
            val comment = comments.getOrNull(index).orEmpty()
            ParsedAttendanceRow(
                date = dates[index],
                comment = comment,
                adjustmentHalfPoints = pointAdjustment.find(comment)?.groupValues?.get(1)?.let(::toHalfPoints),
                runningTotalHalfPoints = totals[index],
            )
        }
        return inferMissingAdjustments(rawRows)
    }

    private fun inferMissingAdjustments(rows: List<ParsedAttendanceRow>): List<ParsedAttendanceRow> =
        rows.mapIndexed { index, row ->
            if (row.adjustmentHalfPoints != null || index == 0) row
            else row.copy(adjustmentHalfPoints = row.runningTotalHalfPoints - rows[index - 1].runningTotalHalfPoints)
        }

    private fun parseDate(value: String): LocalDate? {
        val parts = value.replace(" ", "").split(Regex("[-/.]")).mapNotNull(String::toIntOrNull)
        if (parts.size != 3) return null
        val year = if (parts[2] < 100) 2000 + parts[2] else parts[2]
        return runCatching { LocalDate.of(year, parts[0], parts[1]) }.getOrNull()
    }

    private fun normalize(value: String): String = value
        .replace('−', '-')
        .replace('—', '-')
        .replace(Regex("-\\s+(?=\\d|\\.)"), "-")
        .trim()

    private fun toHalfPoints(value: String): Int? {
        val decimal = normalize(value).toBigDecimalOrNull() ?: return null
        val doubled = decimal.multiply("2".toBigDecimal())
        return doubled.toInt().takeIf { candidate -> doubled.compareTo(candidate.toBigDecimal()) == 0 }
    }
}
