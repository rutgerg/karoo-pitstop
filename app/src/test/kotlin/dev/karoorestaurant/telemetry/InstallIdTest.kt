package dev.karoorestaurant.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InstallIdTest {

    @Test
    fun `generates a valid UUID v4 on first read`() {
        val prefs = FakeSharedPreferences()
        val id = installId(prefs)
        val parsed = UUID.fromString(id)
        assertEquals(4, parsed.version(), "must be UUID version 4")
    }

    @Test
    fun `returns the same id on subsequent reads`() {
        val prefs = FakeSharedPreferences()
        val first = installId(prefs)
        val second = installId(prefs)
        val third = installId(prefs)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `persists the id under the install_id key`() {
        val prefs = FakeSharedPreferences()
        val id = installId(prefs)
        assertEquals(id, prefs.getString(KEY_INSTALL_ID, null))
    }

    @Test
    fun `returns the pre-existing id when one is already stored`() {
        val prefs = FakeSharedPreferences()
        val seeded = "11111111-2222-4333-8444-555555555555"
        prefs.edit().putString(KEY_INSTALL_ID, seeded).apply()

        assertEquals(seeded, installId(prefs))
    }
}
