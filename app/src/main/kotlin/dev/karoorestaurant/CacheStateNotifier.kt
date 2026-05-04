package dev.karoorestaurant

import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Observes [RouteWatcher]'s state and surfaces transitions as Karoo `SystemNotification`s
 * so the rider sees the corridor-cache outcome from the device's notification surface,
 * without needing to open the data page.
 *
 * Single stable [NOTIFICATION_ID] means the latest status replaces the previous one
 * instead of stacking. Idle and Fetching transitions are silent — we only notify
 * once the fetch resolves either way.
 */
class CacheStateNotifier(
    private val systemPort: KarooSystemPort,
    private val header: String,
    private val successFormat: (poiCount: Int, routeName: String) -> String,
    private val failureMessage: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    fun observe(state: StateFlow<RouteFetchState>) {
        scope.launch {
            state.collect { dispatchFor(it) }
        }
    }

    private fun dispatchFor(state: RouteFetchState) {
        val notification = when (state) {
            is RouteFetchState.Cached -> SystemNotification(
                id = NOTIFICATION_ID,
                header = header,
                message = successFormat(state.poiCount, state.routeName),
                style = SystemNotification.Style.EVENT,
            )
            is RouteFetchState.Error -> SystemNotification(
                id = NOTIFICATION_ID,
                header = header,
                message = failureMessage,
                subText = state.message,
                style = SystemNotification.Style.ERROR,
            )
            RouteFetchState.Idle, is RouteFetchState.Fetching -> return
        }
        systemPort.dispatch(notification)
    }

    companion object {
        const val NOTIFICATION_ID: String = "pitstop_cache_status"
    }
}
