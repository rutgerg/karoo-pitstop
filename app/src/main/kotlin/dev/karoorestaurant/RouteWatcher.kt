package dev.karoorestaurant

import android.util.Log
import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RouteWatcher(
    private val karoo: KarooClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val _state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
    val state: StateFlow<RouteFetchState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            karoo.routeFlow.collectLatest { route ->
                if (route == null) {
                    _state.value = RouteFetchState.Idle
                } else if (karoo.store().wasRouteFetched(route.id)) {
                    _state.value = RouteFetchState.Cached(route.name, karoo.store().count())
                    Log.i(TAG, "route ${route.id} already cached")
                } else {
                    fetch(route)
                }
            }
        }
    }

    private suspend fun fetch(route: Route) {
        _state.value = RouteFetchState.Fetching(route.name)
        Log.i(TAG, "fetching corridor for ${route.name}")
        try {
            val samples = CorridorSlicer.sample(route.polyline)
            val count = karoo.refreshAroundCorridor(samples)
            karoo.store().recordRouteFetch(route.id)
            _state.value = RouteFetchState.Cached(route.name, count)
        } catch (t: Throwable) {
            Log.e(TAG, "fetch failed for ${route.name}: ${t.message}")
            _state.value = RouteFetchState.Error(t.message ?: "Unknown error")
        }
    }

    private companion object {
        const val TAG = "RouteWatcher"
    }
}

sealed class RouteFetchState {
    data object Idle : RouteFetchState()
    data class Fetching(val routeName: String) : RouteFetchState()
    data class Cached(val routeName: String, val poiCount: Int) : RouteFetchState()
    data class Error(val message: String) : RouteFetchState()
}
