package dev.karoorestaurant.telemetry

import java.util.concurrent.atomic.AtomicInteger

internal class TelemetryCounters {
    private val tileRenders = AtomicInteger(0)
    private val prefetch = AtomicInteger(0)

    fun incrementTileRender() {
        tileRenders.incrementAndGet()
    }

    fun incrementPrefetch() {
        prefetch.incrementAndGet()
    }

    fun snapshotAndReset(): Snapshot =
        Snapshot(tileRenders.getAndSet(0), prefetch.getAndSet(0))

    fun restore(snapshot: Snapshot) {
        tileRenders.addAndGet(snapshot.tileRenders)
        prefetch.addAndGet(snapshot.prefetch)
    }

    data class Snapshot(val tileRenders: Int, val prefetch: Int)
}
