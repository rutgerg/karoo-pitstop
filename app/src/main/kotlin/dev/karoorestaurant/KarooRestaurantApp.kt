package dev.karoorestaurant

import android.app.Application
import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.db.AndroidPoiStore
import dev.karoorestaurant.settings.SettingsRepository
import dev.karoorestaurant.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

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

    lateinit var skipStore: SkipStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        skipStore = SkipStore().also { it.resetOnRouteChange(karoo.routeFlow.map { route -> route?.id }, appScope) }

        CacheStateNotifier(
            systemPort = systemPort,
            header = getString(R.string.app_name),
            successFormat = { count, name -> getString(R.string.notif_cache_success, count, name) },
            failureMessage = getString(R.string.notif_cache_failure),
        ).observe(routeWatcher.state)
    }
}
