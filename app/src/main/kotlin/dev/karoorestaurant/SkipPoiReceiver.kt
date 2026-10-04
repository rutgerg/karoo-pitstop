package dev.karoorestaurant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.karoorestaurant.data.poi.PoiCategory

/** Tile skip-chevron broadcast: marks the shown POI as skipped so the tile advances to the next one. */
class SkipPoiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KarooRestaurantApp ?: return
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY) ?: return
        val category = runCatching { PoiCategory.valueOf(categoryName) }.getOrNull() ?: return
        if (!intent.hasExtra(EXTRA_OSM_ID)) return
        val osmId = intent.getLongExtra(EXTRA_OSM_ID, 0L)
        val wrapped = intent.getBooleanExtra(EXTRA_WRAPPED, false)
        Log.i(TAG, "tile skip → $category osm $osmId wrapped=$wrapped")
        app.skipStore.skip(category, osmId, wrapped)
    }

    companion object {
        const val ACTION = "dev.karoorestaurant.SKIP_POI"
        const val EXTRA_OSM_ID = "osm_id"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_WRAPPED = "wrapped"
        private const val TAG = "SkipPoiRcvr"
    }
}
