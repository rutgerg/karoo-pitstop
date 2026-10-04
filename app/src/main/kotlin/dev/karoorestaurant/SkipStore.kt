package dev.karoorestaurant

import dev.karoorestaurant.data.poi.PoiCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-category set of POIs the rider has skipped from a tile (issue #63). A skipped POI is no
 * longer offered as the tile pick, so the tile advances to the next-nearest candidate.
 *
 * Skips live in memory only: they are a reaction to the ride in progress (the picked shop turned
 * out to be shut), not a preference. [resetOnRouteChange] clears them whenever the planned route
 * changes so a skip from a previous ride never hides a POI on the next one.
 */
class SkipStore {

    private val _skipped = MutableStateFlow<Map<PoiCategory, Set<Long>>>(emptyMap())
    val skipped: StateFlow<Map<PoiCategory, Set<Long>>> = _skipped.asStateFlow()

    /**
     * Skip [osmId] for [category]. When [osmId] is already skipped the tile has wrapped around to
     * it (every candidate was skipped), so a fresh cycle starts: only [osmId] stays skipped and
     * the tile advances to the second-nearest again.
     */
    fun skip(category: PoiCategory, osmId: Long) {
        _skipped.update { map ->
            val current = map[category].orEmpty()
            val next = if (osmId in current) setOf(osmId) else current + osmId
            map + (category to next)
        }
    }

    fun clear() {
        _skipped.value = emptyMap()
    }

    /** Clears every skip each time the route id changes, including to or from no route. */
    fun resetOnRouteChange(routeIds: Flow<String?>, scope: CoroutineScope) {
        scope.launch {
            routeIds.distinctUntilChanged().collect { clear() }
        }
    }
}
