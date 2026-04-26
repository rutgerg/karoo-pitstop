package dev.karoorestaurant

import android.content.Context
import android.util.Log
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.db.AndroidPoiStore

class KarooClient(context: Context) {

    private val store = AndroidPoiStore(context.applicationContext)
    private val overpass = OverpassClient()

    fun store(): AndroidPoiStore = store

    suspend fun refreshAround(center: LatLng, radiusMeters: Int = 10_000): Int {
        val pois = overpass.fetchCorridor(listOf(center), radiusMeters)
        store.upsertAll(pois)
        Log.i(TAG, "refresh: cached ${pois.size} POIs around $center")
        return pois.size
    }

    fun navigateTo(poi: Poi) {
        Log.i(TAG, "navigateTo placeholder: ${poi.name} at ${poi.lat},${poi.lon}")
    }

    fun close() {
        store.close()
    }

    private companion object {
        const val TAG = "KarooClient"
    }
}
