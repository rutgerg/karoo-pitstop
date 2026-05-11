package dev.karoorestaurant

import android.app.Application
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.db.AndroidPoiStore
import dev.karoorestaurant.settings.SettingsRepository
import dev.karoorestaurant.telemetry.Telemetry

class KarooRestaurantApp : Application() {

    lateinit var karoo: KarooClient
        private set

    lateinit var routeWatcher: RouteWatcher
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var telemetry: Telemetry
        private set

    lateinit var fetchDiary: FetchDiary
        private set

        lateinit var periodicRefresh: PeriodicRefresh
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        telemetry = Telemetry(this, telemetryEnabled = { settings.telemetryEnabled.value })
        fetchDiary = FetchDiary(this)
        val systemPort = RealKarooSystemPort(this)
        karoo = KarooClient(
            karooSystem = systemPort,
            store = AndroidPoiStore(this),
            overpass = OverpassClient(),
        )
        routeWatcher = RouteWatcher(
            karoo,
            telemetry = telemetry,
            diary = fetchDiary,
            connectivity = AndroidConnectivityWatcher(this),
        ).also { it.start() }

        periodicRefresh = PeriodicRefresh(karoo, diary = fetchDiary).also { it.start() }

        CacheStateNotifier(
            systemPort = systemPort,
            header = getString(R.string.app_name),
            successFormat = { count, name -> getString(R.string.notif_cache_success, count, name) },
            failureMessage = getString(R.string.notif_cache_failure),
        ).observe(routeWatcher.state)
    }
}
