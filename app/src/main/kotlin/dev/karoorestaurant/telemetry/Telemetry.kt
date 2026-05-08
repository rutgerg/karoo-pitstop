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

internal const val PREFS_NAME = "telemetry"

class Telemetry internal constructor(
    private val prefs: SharedPreferences,
    private val send: (HeartbeatPayload) -> Boolean,
    private val scope: CoroutineScope,
    private val canSend: () -> Boolean,
) {
    constructor(context: Context, telemetryEnabled: () -> Boolean) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        send = HeartbeatSender()::send,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        canSend = {
            telemetryEnabled() &&
                BuildConfig.SUPABASE_URL.isNotEmpty() &&
                BuildConfig.SUPABASE_ANON_KEY.isNotEmpty()
        },
    )

    private val installId: String = installId(prefs)
    private val counters = TelemetryCounters(prefs)
    private val sending = AtomicBoolean(false)

    fun recordTileRender() {
        counters.incrementTileRender(todayUtc())
        trySend()
    }

    fun recordPrefetch() {
        counters.incrementPrefetch(todayUtc())
        trySend()
    }

    private fun trySend() {
        if (!canSend()) return
        if (!sending.compareAndSet(false, true)) return

        scope.launch {
            try {
                var lastSent: TelemetryCounters.Snapshot? = null
                while (true) {
                    val today = todayUtc()
                    val snapshot = counters.snapshot(today)
                    if (snapshot == lastSent) break
                    val payload = HeartbeatPayload(
                        install_id = installId,
                        day = today,
                        tile_renders = snapshot.tileRenders,
                        prefetch_count = snapshot.prefetch,
                        app_version = BuildConfig.VERSION_NAME,
                    )
                    if (!send(payload)) {
                        Log.w(TAG, "heartbeat failed for $today")
                        break
                    }
                    Log.i(
                        TAG,
                        "heartbeat sent for $today: tiles=${snapshot.tileRenders} prefetch=${snapshot.prefetch}",
                    )
                    lastSent = snapshot
                }
            } finally {
                sending.set(false)
            }
        }
    }

    companion object {
        private const val TAG = "Telemetry"

        private fun todayUtc(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
    }
}
