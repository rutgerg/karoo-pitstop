package dev.karoorestaurant.telemetry

import android.content.SharedPreferences

internal const val KEY_COUNTERS_DAY = "counters_day"
internal const val KEY_TILE_RENDERS = "counters_tile_renders"
internal const val KEY_PREFETCH = "counters_prefetch"

internal class TelemetryCounters(private val prefs: SharedPreferences) {

    @Synchronized
    fun incrementTileRender(today: String) {
        val current = readForToday(today)
        write(today, current.copy(tileRenders = current.tileRenders + 1))
    }

    @Synchronized
    fun incrementPrefetch(today: String) {
        val current = readForToday(today)
        write(today, current.copy(prefetch = current.prefetch + 1))
    }

    @Synchronized
    fun snapshot(today: String): Snapshot = readForToday(today)

    private fun readForToday(today: String): Snapshot {
        if (prefs.getString(KEY_COUNTERS_DAY, null) != today) return Snapshot(0, 0)
        return Snapshot(
            tileRenders = prefs.getInt(KEY_TILE_RENDERS, 0),
            prefetch = prefs.getInt(KEY_PREFETCH, 0),
        )
    }

    private fun write(today: String, snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_COUNTERS_DAY, today)
            .putInt(KEY_TILE_RENDERS, snapshot.tileRenders)
            .putInt(KEY_PREFETCH, snapshot.prefetch)
            .apply()
    }

    data class Snapshot(val tileRenders: Int, val prefetch: Int)
}
