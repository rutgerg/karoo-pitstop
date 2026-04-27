package dev.karoorestaurant

import android.content.Context
import android.util.Log
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.data.route.Polyline
import dev.karoorestaurant.data.route.Route
import dev.karoorestaurant.db.AndroidPoiStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

class KarooClient(context: Context) {

    private val karooSystem = KarooSystemService(context.applicationContext)
    private val store = AndroidPoiStore(context.applicationContext)
    private val overpass = OverpassClient()

    init {
        karooSystem.connect { connected -> Log.i(TAG, "KarooSystem connected=$connected") }
    }

    val locationFlow: Flow<LatLng> = consumerFlow<OnLocationChanged>()
        .map { LatLng(it.lat, it.lng) }

    val routeFlow: Flow<Route?> = consumerFlow<OnNavigationState>()
        .map { it.state.toRoute() }
        .distinctUntilChangedBy { it?.id }

    fun store(): AndroidPoiStore = store

    suspend fun refreshAround(center: LatLng, radiusMeters: Int = 10_000): Int {
        val pois = overpass.fetchCorridor(listOf(center), radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh: cached ${pois.size} POIs around $center")
        return pois.size
    }

    suspend fun refreshAroundCorridor(samples: List<LatLng>, radiusMeters: Int = 10_000): Int {
        val pois = overpass.fetchCorridor(samples, radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh corridor: ${samples.size} samples → ${pois.size} POIs")
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
        store.close()
    }

    private inline fun <reified T : KarooEvent> consumerFlow(): Flow<T> = callbackFlow {
        val id = karooSystem.addConsumer<T> { trySend(it) }
        awaitClose { karooSystem.removeConsumer(id) }
    }

    private companion object {
        const val TAG = "KarooClient"
    }
}
