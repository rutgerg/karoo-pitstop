package dev.karoorestaurant

import android.util.Log
import dev.karoorestaurant.data.route.LatLng
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
    private val diary: FetchDiary? = null,
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
                val center = location.point
                try {
                    val count = karoo.refreshAround(center, radiusMeters)
                    Log.i(TAG, "periodic refresh ok: $count POIs around $center")
                    diary?.record(periodicEntry(center, FetchDiary.Status.SUCCESS, poisFetched = count))
                } catch (t: Throwable) {
                    // Most common cause: no internet. Next tick will try again.
                    Log.i(TAG, "periodic refresh failed: ${t.message}")
                    diary?.record(periodicEntry(center, FetchDiary.Status.ERROR, errorMessage = t.message))
                }
            }
        }
    }

    private fun periodicEntry(
        center: LatLng,
        status: FetchDiary.Status,
        poisFetched: Int? = null,
        errorMessage: String? = null,
    ): FetchDiary.Entry = FetchDiary.Entry(
        kind = FetchDiary.Kind.PERIODIC,
        routeName = PERIODIC_LABEL,
        routeId = PERIODIC_LABEL,
        polylineLength = 0,
        polylineStartLat = center.lat,
        polylineStartLon = center.lon,
        polylineEndLat = null,
        polylineEndLon = null,
        windowCount = 1,
        attempts = 1,
        status = status,
        errorMessage = errorMessage,
        poisFetched = poisFetched,
    )

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 20 * 60_000L
        const val DEFAULT_RADIUS_METERS: Int = 10_000
        private const val PERIODIC_LABEL = "periodic"
        private const val TAG = "PeriodicRefresh"
    }
}
