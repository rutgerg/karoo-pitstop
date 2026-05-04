package dev.karoorestaurant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.karoorestaurant.data.route.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SeedPoisReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val isDebug = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) {
            Log.w(TAG, "ignoring SEED_POIS on non-debug build")
            return
        }
        val lat = intent.getStringExtra(EXTRA_LAT)?.toDoubleOrNull()
        val lon = intent.getStringExtra(EXTRA_LON)?.toDoubleOrNull()
        if (lat == null || lon == null) {
            Log.w(TAG, "SEED_POIS missing or unparseable lat/lon")
            return
        }
        val radius = intent.getStringExtra(EXTRA_RADIUS)?.toIntOrNull() ?: DEFAULT_RADIUS

        val karooApp = app as KarooRestaurantApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val n = karooApp.karoo.refreshAround(LatLng(lat, lon), radius)
                Log.i(TAG, "SEED_POIS cached $n POIs around ($lat, $lon) r=${radius}m")
            } catch (t: Throwable) {
                Log.e(TAG, "SEED_POIS failed: ${t.message}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "dev.karoorestaurant.SEED_POIS"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_RADIUS = "radius"
        private const val DEFAULT_RADIUS = 5_000
        private const val TAG = "SeedPoisRcvr"
    }
}
