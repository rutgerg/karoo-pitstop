package dev.karoorestaurant.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.karoorestaurant.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

class Telemetry(
    context: Context,
    private val sender: HeartbeatSender = HeartbeatSender(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val installId: String = installId(prefs)
    private val counters = TelemetryCounters()
    private val sending = AtomicBoolean(false)

    init {
        tryHeartbeat()
    }

    fun recordTileRender() {
        counters.incrementTileRender()
        tryHeartbeat()
    }

    fun recordPrefetch() {
        counters.incrementPrefetch()
        tryHeartbeat()
    }

    private fun tryHeartbeat() {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        if (BuildConfig.SUPABASE_URL.isEmpty() || BuildConfig.SUPABASE_ANON_KEY.isEmpty()) return
        val today = todayUtc()
        if (prefs.getString(KEY_LAST_SENT_DAY, null) == today) return
        if (!sending.compareAndSet(false, true)) return

        scope.launch {
            try {
                if (prefs.getString(KEY_LAST_SENT_DAY, null) == today) return@launch
                val snapshot = counters.snapshotAndReset()
                val payload = HeartbeatPayload(
                    install_id = installId,
                    day = today,
                    tile_renders = snapshot.tileRenders,
                    prefetch_count = snapshot.prefetch,
                    app_version = BuildConfig.VERSION_NAME,
                )
                if (sender.send(payload)) {
                    prefs.edit().putString(KEY_LAST_SENT_DAY, today).apply()
                    Log.i(TAG, "heartbeat sent for $today")
                } else {
                    counters.restore(snapshot)
                }
            } finally {
                sending.set(false)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "telemetry"
        private const val KEY_LAST_SENT_DAY = "last_sent_day"
        private const val TAG = "Telemetry"

        private fun todayUtc(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
    }
}
