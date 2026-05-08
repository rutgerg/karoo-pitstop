package dev.karoorestaurant.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryCountersTest {

    @Test
    fun `tile render and prefetch increment independently for the same day`() {
        val counters = TelemetryCounters(FakeSharedPreferences())
        counters.incrementTileRender("2026-05-08")
        counters.incrementTileRender("2026-05-08")
        counters.incrementPrefetch("2026-05-08")

        val snapshot = counters.snapshot("2026-05-08")
        assertEquals(2, snapshot.tileRenders)
        assertEquals(1, snapshot.prefetch)
    }

    @Test
    fun `snapshot returns zero when day differs from persisted day`() {
        val counters = TelemetryCounters(FakeSharedPreferences())
        counters.incrementTileRender("2026-05-08")
        counters.incrementPrefetch("2026-05-08")

        val tomorrow = counters.snapshot("2026-05-09")
        assertEquals(0, tomorrow.tileRenders)
        assertEquals(0, tomorrow.prefetch)
    }

    @Test
    fun `incrementing on a new day resets the counters`() {
        val counters = TelemetryCounters(FakeSharedPreferences())
        counters.incrementTileRender("2026-05-08")
        counters.incrementTileRender("2026-05-08")
        counters.incrementPrefetch("2026-05-08")

        counters.incrementTileRender("2026-05-09")

        val today = counters.snapshot("2026-05-09")
        assertEquals(1, today.tileRenders)
        assertEquals(0, today.prefetch)

        val yesterday = counters.snapshot("2026-05-08")
        assertEquals(0, yesterday.tileRenders, "previous day is no longer the persisted day")
    }

    @Test
    fun `counters survive across instances using the same SharedPreferences`() {
        val prefs = FakeSharedPreferences()
        TelemetryCounters(prefs).apply {
            incrementTileRender("2026-05-08")
            incrementPrefetch("2026-05-08")
            incrementTileRender("2026-05-08")
        }

        val snapshot = TelemetryCounters(prefs).snapshot("2026-05-08")
        assertEquals(2, snapshot.tileRenders)
        assertEquals(1, snapshot.prefetch)
    }
}
