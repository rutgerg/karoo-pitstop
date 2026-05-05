package dev.karoorestaurant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension

class RestaurantExtensionService : KarooExtension(EXTENSION_ID, EXTENSION_VERSION) {

    override val types: List<DataTypeImpl> by lazy {
        val app = application as KarooRestaurantApp
        listOf(
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.RESTAURANT, NearbyPoiDataType.TYPE_RESTAURANT),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.SUPERMARKET, NearbyPoiDataType.TYPE_SUPERMARKET),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.FUEL, NearbyPoiDataType.TYPE_FUEL),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.CAFE, NearbyPoiDataType.TYPE_CAFE),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.HOTEL, NearbyPoiDataType.TYPE_HOTEL),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.DOCTOR, NearbyPoiDataType.TYPE_DOCTOR),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.PHARMACY, NearbyPoiDataType.TYPE_PHARMACY),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.BIKE_SHOP, NearbyPoiDataType.TYPE_BIKE_SHOP),
            NearbyPoiDataType(app.karoo, dev.karoorestaurant.data.poi.PoiCategory.ATM, NearbyPoiDataType.TYPE_ATM),
        )
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Restaurant extension",
                NotificationManager.IMPORTANCE_MIN,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_pitstop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTENSION_ID = "restaurant"
        const val EXTENSION_VERSION = "1.0.0"
        private const val CHANNEL_ID = "restaurant_extension_status"
        private const val NOTIFICATION_ID = 1
    }
}
