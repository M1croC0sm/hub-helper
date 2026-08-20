package app.hubhelper

import android.content.Context
import java.time.LocalDate

interface DebugDateController {
    val overrideDate: LocalDate?
    fun setOverride(date: LocalDate?)
}

fun createDebugDateController(context: Context): DebugDateController =
    platformDebugDateController(context)

internal fun LocalDate.isoDate(): String = toString()

