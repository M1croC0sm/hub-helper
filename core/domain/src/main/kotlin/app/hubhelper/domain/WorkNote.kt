package app.hubhelper.domain

import java.time.LocalDate

data class WorkNote(
    val id: String,
    val date: LocalDate,
    val text: String,
)

