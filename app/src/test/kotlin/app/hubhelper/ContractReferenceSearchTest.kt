package app.hubhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractReferenceSearchTest {
    @Test
    fun `search result retains document and exact line destination`() {
        val results = searchReferenceLines(
            listOf(
                "Contract title\nVacation language\nHoliday language",
                "Policy title\nSeven points\nPerfect attendance",
            ),
            "points",
        )

        assertEquals(listOf(ReferenceSearchMatch(1, 1, "Seven points")), results)
    }

    @Test
    fun `search is case insensitive and ignores one character queries`() {
        assertEquals("Holiday language", searchReferenceLines(listOf("Title\nHoliday language"), "HOLIDAY").single().line)
        assertTrue(searchReferenceLines(listOf("A"), "A").isEmpty())
    }
}
