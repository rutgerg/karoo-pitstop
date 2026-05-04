package dev.karoorestaurant

import android.util.Log
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.CorridorSlicer
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.data.route.Polyline
import dev.karoorestaurant.data.route.Route
import dev.karoorestaurant.db.PoiStore
import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

typealias OverpassFetcher = suspend (windows: List<List<LatLng>>, radiusMeters: Int) -> List<Poi>

class KarooClient(
    private val karooSystem: KarooSystemPort,
    private val store: PoiStore,
    private val overpass: OverpassFetcher,
) {

    init {
        karooSystem.connect { connected -> Log.i(TAG, "KarooSystem connected=$connected") }
    }

    private val testLocationFlow = MutableSharedFlow<LatLng>(extraBufferCapacity = 8)

    val locationFlow: Flow<LatLng> = merge(
        callbackFlow {
            val id = karooSystem.observeLocations { trySend(it) }
            awaitClose { karooSystem.removeConsumer(id) }
        }.map { LatLng(it.lat, it.lng) },
        testLocationFlow,
    )

    val routeFlow: Flow<Route?> = callbackFlow {
        val id = karooSystem.observeNavigationStates { trySend(it) }
        awaitClose { karooSystem.removeConsumer(id) }
    }.map { it.state.toRoute() }
        .distinctUntilChangedBy { it?.id }

    fun injectTestLocation(location: LatLng) {
        val sent = testLocationFlow.tryEmit(location)
        Log.i(TAG, "injectTestLocation $location sent=$sent")
    }

    fun store(): PoiStore = store

    suspend fun refreshAround(center: LatLng, radiusMeters: Int = 10_000): Int {
        val pois = overpass(listOf(listOf(center)), radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh: cached ${pois.size} POIs around $center")
        return pois.size
    }

    suspend fun refreshAroundCorridor(polyline: List<LatLng>, radiusMeters: Int = 10_000): Int {
        val windows = CorridorSlicer.windows(polyline)
        if (windows.isEmpty()) {
            Log.w(TAG, "refresh corridor: empty polyline, nothing to fetch")
            return 0
        }
        val pois = overpass(windows, radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh corridor: ${windows.size} windows → ${pois.size} POIs")
        return pois.size
    }

    fun navigateTo(poi: Poi) {
        val pin = Symbol.POI(
            id = "osm-${poi.osmType}-${poi.osmId}",
            lat = poi.lat,
            lng = poi.lon,
            type = poi.category.toSymbolType(),
            name = poi.name,
        )
        val dispatched = karooSystem.dispatch(LaunchPinDrop(pin))
        if (dispatched) {
            Log.i(TAG, "navigateTo: dispatched LaunchPinDrop for ${poi.name}")
        } else {
            Log.w(TAG, "navigateTo: KarooSystem not connected; dropped pin for ${poi.name}")
        }
    }

    private fun PoiCategory.toSymbolType(): String = when (this) {
        PoiCategory.RESTAURANT -> Symbol.POI.Types.FOOD
        PoiCategory.SUPERMARKET -> Symbol.POI.Types.SHOPPING
        PoiCategory.FUEL -> Symbol.POI.Types.GAS_STATION
    }

    private fun OnNavigationState.NavigationState.toRoute(): Route? = when (this) {
        is OnNavigationState.NavigationState.Idle -> null
        is OnNavigationState.NavigationState.NavigatingRoute -> Route(
            id = routePolyline.hashCode().toString(),
            name = name,
            polyline = Polyline.decode(routePolyline),
            distanceMeters = routeDistance,
        )
        is OnNavigationState.NavigationState.NavigatingToDestination -> Route(
            id = polyline.hashCode().toString(),
            name = destination.name ?: "Destination",
            polyline = Polyline.decode(polyline),
            distanceMeters = 0.0,
        )
    }

    fun close() {
        karooSystem.disconnect()
    }

    private companion object {
        const val TAG = "KarooClient"
    }
}

internal fun defaultOverpassFetcher(client: OverpassClient = OverpassClient()): OverpassFetcher =
    { samples, radius -> client.fetchCorridor(samples, radius) }
