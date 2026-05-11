package dev.karoorestaurant

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PeriodicRefresh(
    private val karoo: KarooClient,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val latestLocation = MutableStateFlow<RiderLocation?>(null)

    fun start() {
        scope.launch {
            karoo.locationFlow.collect { latestLocation.value = it }
        }
        scope.launch {
            while (true) {
                delay(intervalMs)
                val location = latestLocation.value
                if (location == null) {
                    Log.i(TAG, "skip periodic refresh: no location yet")
                    continue
                }
                try {
                    val count = karoo.refreshAround(location.point, radiusMeters)
                    Log.i(TAG, "periodic refresh ok: $count POIs around ${location.point}")
                } catch (t: Throwable) {
                    // Most common cause: no internet. Next tick will try again.
                    Log.i(TAG, "periodic refresh failed: ${t.message}")
                }
            }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 20 * 60_000L
        const val DEFAULT_RADIUS_METERS: Int = 10_000
        private const val TAG = "PeriodicRefresh"
    }
}
