package app.hubhelper

import android.content.Context
import androidx.compose.runtime.Composable
import java.time.LocalDate

internal fun platformDebugDateController(context: Context): DebugDateController =
    object : DebugDateController {
        override val overrideDate: LocalDate? = null
        override fun setOverride(date: LocalDate?) = Unit
    }

@Composable
fun DebugTools(
    appDate: LocalDate,
    overrideDate: LocalDate?,
    onOverrideChanged: (LocalDate?) -> Unit,
) = Unit

