package dev.karoorestaurant

import android.app.Application

class KarooRestaurantApp : Application() {

    lateinit var karoo: KarooClient
        private set

    lateinit var routeWatcher: RouteWatcher
        private set

    override fun onCreate() {
        super.onCreate()
        karoo = KarooClient(this)
        routeWatcher = RouteWatcher(karoo).also { it.start() }
    }
}
