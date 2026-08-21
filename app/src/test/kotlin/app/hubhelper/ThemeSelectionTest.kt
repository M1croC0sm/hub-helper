package app.hubhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSelectionTest {
    @Test
    fun `three visual themes are available`() {
        assertEquals(
            listOf("Industrial Instrument", "Clear & Easy", "Soft & Friendly"),
            HubTheme.entries.map(HubTheme::displayName),
        )
    }

    @Test
    fun `theme modes resolve light dark and system correctly`() {
        assertEquals(false, ThemeMode.LIGHT.resolveDarkMode(systemDark = true))
        assertEquals(true, ThemeMode.DARK.resolveDarkMode(systemDark = false))
        assertEquals(false, ThemeMode.SYSTEM.resolveDarkMode(systemDark = false))
        assertEquals(true, ThemeMode.SYSTEM.resolveDarkMode(systemDark = true))
    }
}
