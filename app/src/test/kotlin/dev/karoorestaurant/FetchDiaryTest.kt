package dev.karoorestaurant

import dev.karoorestaurant.telemetry.FakeSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FetchDiaryTest {

    private fun sample(
        routeId: String = "r1",
        status: FetchDiary.Status = FetchDiary.Status.SUCCESS,
        poisFetched: Int? = 42,
        errorMessage: String? = null,
    ) = FetchDiary.Entry(
        routeName = "Test",
        routeId = routeId,
        polylineLength = 10,
        polylineStartLat = 52.0,
        polylineStartLon = 4.0,
        polylineEndLat = 52.1,
        polylineEndLon = 4.1,
        windowCount = 1,
        attempts = 1,
        status = status,
        errorMessage = errorMessage,
        poisFetched = poisFetched,
    )

    @Test
    fun `record stamps entry with current epoch millis and persists across instances`() {
        val prefs = FakeSharedPreferences()
        var now = 1_700_000_000_000L
        FetchDiary(prefs, nowEpochMillis = { now }).record(sample())

        val reread = FetchDiary(prefs, nowEpochMillis = { now + 1 }).recent()
        assertEquals(1, reread.size)
        assertEquals(1_700_000_000_000L, reread.single().atEpochMillis)
        assertEquals(42, reread.single().poisFetched)
    }

    @Test
    fun `ring buffer drops oldest entries past capacity`() {
        val prefs = FakeSharedPreferences()
        val diary = FetchDiary(prefs, nowEpochMillis = { 1L }, capacity = 3)
        diary.record(sample(routeId = "r1"))
        diary.record(sample(routeId = "r2"))
        diary.record(sample(routeId = "r3"))
        diary.record(sample(routeId = "r4"))

        val entries = diary.recent()
        assertEquals(3, entries.size)
        assertEquals(listOf("r2", "r3", "r4"), entries.map { it.routeId })
    }

    @Test
    fun `error entries roundtrip with errorMessage and null poisFetched`() {
        val prefs = FakeSharedPreferences()
        val diary = FetchDiary(prefs, nowEpochMillis = { 1L })
        diary.record(
            sample(status = FetchDiary.Status.ERROR, errorMessage = "boom", poisFetched = null),
        )

        val entry = diary.recent().single()
        assertEquals(FetchDiary.Status.ERROR, entry.status)
        assertEquals("boom", entry.errorMessage)
        assertNull(entry.poisFetched)
    }

    @Test
    fun `recent returns empty list when nothing recorded`() {
        val diary = FetchDiary(FakeSharedPreferences())
        assertEquals(emptyList<FetchDiary.Entry>(), diary.recent())
    }

    @Test
    fun `corrupted prefs payload falls back to empty list rather than crashing`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("entries", "not json").apply()
        val diary = FetchDiary(prefs)
        assertEquals(emptyList<FetchDiary.Entry>(), diary.recent())
        // Still usable after corruption: a fresh record overwrites the bad payload.
        diary.record(sample())
        assertNotNull(diary.recent().singleOrNull())
    }
}
