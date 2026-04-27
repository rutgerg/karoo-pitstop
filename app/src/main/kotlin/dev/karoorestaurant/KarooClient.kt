package dev.karoorestaurant

import android.content.Context
import android.util.Log
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.db.AndroidPoiStore
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun store(): AndroidPoiStore = store

    suspend fun refreshAround(center: LatLng, radiusMeters: Int = 10_000): Int {
        val pois = overpass.fetchCorridor(listOf(center), radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh: cached ${pois.size} POIs around $center")
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
