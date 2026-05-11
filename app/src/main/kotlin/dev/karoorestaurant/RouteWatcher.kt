package dev.karoorestaurant

import android.util.Log
import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.Route
import dev.karoorestaurant.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RouteWatcher(
    private val karoo: KarooClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val retryCooldownMs: Long = DEFAULT_RETRY_COOLDOWN_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val telemetry: Telemetry? = null,
    private val diary: FetchDiary? = null,
    private val connectivity: ConnectivityWatcher? = null,
) {

    private val _state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
    val state: StateFlow<RouteFetchState> = _state.asStateFlow()

    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var lastRoute: Route? = null

    fun start() {
        scope.launch {
            merge(
                karoo.routeFlow.onEach { lastRoute = it },
                retryTrigger.mapNotNull { lastRoute },
            ).collectLatest { route -> handleRoute(route) }
        }
        val connectivity = connectivity ?: return
        scope.launch {
            connectivity.onAvailable.collect {
                if (_state.value is RouteFetchState.Error && lastRoute != null) {
                    Log.i(TAG, "network available, retrying ${lastRoute?.name}")
                    retryTrigger.tryEmit(Unit)
                }
            }
        }
    }

    private suspend fun handleRoute(route: Route?) {
        if (route == null) {
            _state.value = RouteFetchState.Idle
            return
        }
        if (karoo.store().wasRouteFetched(route.id)) {
            _state.value = RouteFetchState.Cached(route.name, karoo.store().count())
            Log.i(TAG, "route ${route.id} already cached")
            return
        }

        val windowCount = CorridorSlicer.windows(route.polyline).size
        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++
            if (fetchOnce(route, attempts)) {
                recordOutcome(route, windowCount, attempts)
                return
            }
            if (attempts >= maxAttempts) {
                Log.w(TAG, "giving up on ${route.name} after $maxAttempts attempts")
                recordOutcome(route, windowCount, attempts)
                return
            }
            // Wait at least the cooldown, then for the next location signal — whichever
            // is later. A new route arrival cancels this whole block via collectLatest.
            delay(retryCooldownMs)
            Log.i(TAG, "waiting for next location event before retrying ${route.name}")
            karoo.locationFlow.first()
        }
    }

    private fun recordOutcome(route: Route, windowCount: Int, attempts: Int) {
        val diary = diary ?: return
        val first = route.polyline.firstOrNull()
        val last = route.polyline.lastOrNull()
        val base = FetchDiary.Entry(
            routeName = route.name,
            routeId = route.id,
            polylineLength = route.polyline.size,
            polylineStartLat = first?.lat,
            polylineStartLon = first?.lon,
            polylineEndLat = last?.lat,
            polylineEndLon = last?.lon,
            windowCount = windowCount,
            attempts = attempts,
            status = FetchDiary.Status.ERROR,
        )
        val entry = when (val s = _state.value) {
            is RouteFetchState.Cached -> base.copy(
                status = FetchDiary.Status.SUCCESS,
                poisFetched = s.poiCount,
            )
            is RouteFetchState.Error -> base.copy(errorMessage = s.message)
            else -> return
        }
        diary.record(entry)
    }

    private suspend fun fetchOnce(route: Route, attempt: Int): Boolean {
        _state.value = RouteFetchState.Fetching(route.name)
        Log.i(TAG, "fetching corridor for ${route.name} attempt $attempt")
        return try {
            val count = karoo.refreshAroundCorridor(route.polyline)
            karoo.store().recordRouteFetch(route.id)
            telemetry?.recordPrefetch()
            _state.value = RouteFetchState.Cached(route.name, count)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "fetch failed for ${route.name} attempt $attempt: ${t.message}")
            _state.value = RouteFetchState.Error(t.message ?: "Unknown error")
            false
        }
    }

    companion object {
        const val DEFAULT_RETRY_COOLDOWN_MS: Long = 60_000L
        const val DEFAULT_MAX_ATTEMPTS: Int = 3
        private const val TAG = "RouteWatcher"
    }
}

sealed class RouteFetchState {
    data object Idle : RouteFetchState()
    data class Fetching(val routeName: String) : RouteFetchState()
    data class Cached(val routeName: String, val poiCount: Int) : RouteFetchState()
    data class Error(val message: String) : RouteFetchState()
}
