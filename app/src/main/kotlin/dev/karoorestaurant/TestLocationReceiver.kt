package dev.karoorestaurant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.karoorestaurant.data.route.LatLng

class TestLocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val isDebug = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) {
            Log.w(TAG, "ignoring TEST_LOCATION on non-debug build")
            return
        }
        val lat = intent.getStringExtra(EXTRA_LAT)?.toDoubleOrNull()
        val lon = intent.getStringExtra(EXTRA_LON)?.toDoubleOrNull()
        if (lat == null || lon == null) {
            Log.w(TAG, "TEST_LOCATION missing or unparseable lat/lon strings")
            return
        }
        (app as KarooRestaurantApp).karoo.injectTestLocation(LatLng(lat, lon))
    }

    companion object {
        const val ACTION = "dev.karoorestaurant.TEST_LOCATION"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        private const val TAG = "TestLocationRcvr"
    }
}
