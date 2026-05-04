package dev.karoorestaurant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory

class LaunchPoiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KarooRestaurantApp ?: return
        val osmType = intent.getStringExtra(EXTRA_OSM_TYPE) ?: return
        val osmId = intent.getLongExtra(EXTRA_OSM_ID, 0L)
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY) ?: return
        val category = runCatching { PoiCategory.valueOf(categoryName) }.getOrNull() ?: return
        val lat = intent.getStringExtra(EXTRA_LAT)?.toDoubleOrNull() ?: return
        val lon = intent.getStringExtra(EXTRA_LON)?.toDoubleOrNull() ?: return

        val poi = Poi(
            osmType = osmType,
            osmId = osmId,
            name = name,
            category = category,
            lat = lat,
            lon = lon,
            openingHoursTag = null,
        )
        Log.i(TAG, "tile tap → navigateTo ${poi.name}")
        app.karoo.navigateTo(poi)
    }

    companion object {
        const val ACTION = "dev.karoorestaurant.LAUNCH_POI"
        const val EXTRA_OSM_TYPE = "osm_type"
        const val EXTRA_OSM_ID = "osm_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        private const val TAG = "LaunchPoiRcvr"
    }
}
