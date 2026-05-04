package dev.karoorestaurant

import android.app.Application
import dev.karoorestaurant.db.AndroidPoiStore

class KarooRestaurantApp : Application() {

    lateinit var karoo: KarooClient
        private set

    lateinit var routeWatcher: RouteWatcher
        private set

    override fun onCreate() {
        super.onCreate()
        val systemPort = RealKarooSystemPort(this)
        karoo = KarooClient(
            karooSystem = systemPort,
            store = AndroidPoiStore(this),
            overpass = KarooOverpassFetcher(systemPort),
        )
        routeWatcher = RouteWatcher(karoo).also { it.start() }
    }
}
