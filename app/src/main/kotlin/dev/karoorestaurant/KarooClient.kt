package dev.karoorestaurant

import android.util.Log
import dev.karoorestaurant.data.overpass.OverpassFetcher
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

class KarooClient(
    private val karooSystem: KarooSystemPort,
    private val store: PoiStore,
    private val overpass: OverpassFetcher,
) {

    init {
        karooSystem.connect { connected -> Log.i(TAG, "KarooSystem connected=$connected") }
    }

    private val testLocationFlow = MutableSharedFlow<RiderLocation>(extraBufferCapacity = 8)

    val locationFlow: Flow<RiderLocation> = merge(
        callbackFlow {
            val id = karooSystem.observeLocations { trySend(it) }
            awaitClose { karooSystem.removeConsumer(id) }
        }.map { RiderLocation(LatLng(it.lat, it.lng), it.orientation) },
        testLocationFlow,
    )

    val routeFlow: Flow<Route?> = callbackFlow {
        val id = karooSystem.observeNavigationStates { trySend(it) }
        awaitClose { karooSystem.removeConsumer(id) }
    }.map { it.state.toRoute() }
        .distinctUntilChangedBy { it?.id }

    fun injectTestLocation(location: LatLng, orientationDegrees: Double? = null) {
        val sent = testLocationFlow.tryEmit(RiderLocation(location, orientationDegrees))
        Log.i(TAG, "injectTestLocation $location orientation=$orientationDegrees sent=$sent")
    }

    fun store(): PoiStore = store

    suspend fun refreshAround(
        center: LatLng,
        radiusMeters: Int = 10_000,
        categories: Set<PoiCategory>? = null,
    ): Int {
        val pois = overpass(listOf(listOf(center)), radiusMeters, categories)
        store.upsertAll(pois)
        Log.i(TAG, "refresh: cached ${pois.size} POIs around $center categories=${categories ?: "all"}")
        return pois.size
    }

    suspend fun refreshAroundCorridor(polyline: List<LatLng>, radiusMeters: Int = 10_000): Int {
        val windows = CorridorSlicer.windows(polyline)
        if (windows.isEmpty()) {
            Log.w(TAG, "refresh corridor: empty polyline, nothing to fetch")
            return 0
        }
        val pois = overpass(windows, radiusMeters, null)
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
        PoiCategory.CAFE -> Symbol.POI.Types.COFFEE
        PoiCategory.HOTEL -> Symbol.POI.Types.LODGING
        PoiCategory.DOCTOR -> Symbol.POI.Types.HOSPITAL
        PoiCategory.PHARMACY -> Symbol.POI.Types.FIRST_AID
        PoiCategory.BIKE_SHOP -> Symbol.POI.Types.BIKE_SHOP
        PoiCategory.ATM -> Symbol.POI.Types.ATM
        PoiCategory.TRAIN_STATION -> Symbol.POI.Types.TRANSIT_CENTER
        PoiCategory.WATER_REFILL -> Symbol.POI.Types.WATER
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

