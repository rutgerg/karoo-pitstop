package dev.karoorestaurant.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryCountersTest {

    @Test
    fun `tile render and prefetch increment independently`() {
        val counters = TelemetryCounters()
        counters.incrementTileRender()
        counters.incrementTileRender()
        counters.incrementPrefetch()

        val snapshot = counters.snapshotAndReset()
        assertEquals(2, snapshot.tileRenders)
        assertEquals(1, snapshot.prefetch)
    }

    @Test
    fun `snapshotAndReset zeros the counters`() {
        val counters = TelemetryCounters()
        counters.incrementTileRender()
        counters.incrementPrefetch()
        counters.snapshotAndReset()

        val second = counters.snapshotAndReset()
        assertEquals(0, second.tileRenders)
        assertEquals(0, second.prefetch)
    }

    @Test
    fun `restore adds the snapshot back to the live counters`() {
        val counters = TelemetryCounters()
        counters.incrementTileRender()
        counters.incrementPrefetch()
        val snapshot = counters.snapshotAndReset()

        counters.incrementTileRender()
        counters.restore(snapshot)

        val final = counters.snapshotAndReset()
        assertEquals(2, final.tileRenders)
        assertEquals(1, final.prefetch)
    }

    @Test
    fun `restore is a no-op when the snapshot is zero`() {
        val counters = TelemetryCounters()
        counters.incrementTileRender()
        counters.restore(TelemetryCounters.Snapshot(0, 0))

        val snapshot = counters.snapshotAndReset()
        assertEquals(1, snapshot.tileRenders)
        assertEquals(0, snapshot.prefetch)
    }
}
