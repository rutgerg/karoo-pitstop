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
        karoo = KarooClient(
            karooSystem = RealKarooSystemPort(this),
            store = AndroidPoiStore(this),
            overpass = defaultOverpassFetcher(),
        )
        routeWatcher = RouteWatcher(karoo).also { it.start() }
    }
}
