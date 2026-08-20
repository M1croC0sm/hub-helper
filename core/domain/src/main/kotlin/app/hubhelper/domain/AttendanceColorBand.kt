package app.hubhelper.domain

import java.math.BigDecimal

enum class AttendanceColorBand { GREEN, ORANGE, RED }

fun attendanceColorBand(points: BigDecimal): AttendanceColorBand = when {
    points > BigDecimal(5) -> AttendanceColorBand.RED
    points > BigDecimal(3) -> AttendanceColorBand.ORANGE
    else -> AttendanceColorBand.GREEN
}
