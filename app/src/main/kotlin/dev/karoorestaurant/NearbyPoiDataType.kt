package dev.karoorestaurant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.Geo
import dev.karoorestaurant.data.route.LatLng
import dev.karoorestaurant.settings.SettingsRepository
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class NearbyPoiDataType(
    private val karoo: KarooClient,
    private val category: PoiCategory,
    typeId: String,
    private val routeFetchState: StateFlow<RouteFetchState> = MutableStateFlow(RouteFetchState.Idle),
    private val showClosedPois: StateFlow<Boolean> = MutableStateFlow(SettingsRepository.DEFAULT_SHOW_CLOSED_POIS),
) : DataTypeImpl(extension = RestaurantExtensionService.EXTENSION_ID, typeId = typeId) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        (context.applicationContext as? KarooRestaurantApp)?.telemetry?.recordTileRender()

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        emitter.setCancellable { scope.cancel() }

        emitter.updateView(
            buildView(
                context,
                pick = null,
                rider = null,
                placeholderRes = R.string.field_waiting,
                showStaleIcon = false,
            ),
        )

        scope.launch {
            combine(
                karoo.locationFlow.sample(SAMPLE_MS),
                routeFetchState,
                showClosedPois,
            ) { rider, state, showClosed -> Triple(rider, state, showClosed) }.collect { (rider, state, showClosed) ->
                val pick = withContext(Dispatchers.IO) {
                    tilePick(computeNearbyPicks(karoo, rider.point, showClosed), category)
                }
                emitter.updateView(
                    buildView(
                        context,
                        pick = pick,
                        rider = rider,
                        placeholderRes = placeholderFor(state),
                        showStaleIcon = pick != null && state !is RouteFetchState.Cached,
                    ),
                )
            }
        }
    }

    private fun buildView(
        context: Context,
        pick: PoiNearby?,
        rider: RiderLocation?,
        placeholderRes: Int,
        showStaleIcon: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.data_field_nearby_poi)
        val mainText = pick?.let { buildPoiLine(it, rider, statusColor(it.status)) }
            ?: context.getString(placeholderRes)
        views.setTextViewText(R.id.poi_text, mainText)

        views.setViewVisibility(R.id.poi_stale_icon, if (showStaleIcon) View.VISIBLE else View.GONE)

        val hoursText = pick?.let { formatHours(it) }
        if (hoursText.isNullOrBlank()) {
            views.setViewVisibility(R.id.poi_hours, View.GONE)
        } else {
            views.setTextViewText(R.id.poi_hours, hoursText)
            views.setViewVisibility(R.id.poi_hours, View.VISIBLE)
        }

        views.setOnClickPendingIntent(R.id.poi_root, pick?.let { buildLaunchPendingIntent(context, it.poi) })
        return views
    }

    private fun formatHours(pick: PoiNearby): String? =
        if (pick.staleness == Staleness.AGING) "unverified" else null

    private fun statusColor(status: OpeningHours.Status?): Int = when (status) {
        OpeningHours.Status.Open -> COLOR_OPEN
        OpeningHours.Status.Closed -> COLOR_CLOSED
        is OpeningHours.Status.Unknown -> COLOR_UNKNOWN
        null -> COLOR_DEFAULT
    }

    private fun buildLaunchPendingIntent(context: Context, poi: Poi): PendingIntent {
        val intent = Intent(LaunchPoiReceiver.ACTION).apply {
            setClassName(context, LaunchPoiReceiver::class.java.name)
            putExtra(LaunchPoiReceiver.EXTRA_OSM_TYPE, poi.osmType)
            putExtra(LaunchPoiReceiver.EXTRA_OSM_ID, poi.osmId)
            putExtra(LaunchPoiReceiver.EXTRA_NAME, poi.name)
            putExtra(LaunchPoiReceiver.EXTRA_CATEGORY, poi.category.name)
            putExtra(LaunchPoiReceiver.EXTRA_LAT, poi.lat.toString())
            putExtra(LaunchPoiReceiver.EXTRA_LON, poi.lon.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            category.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000.0) "${meters.toInt()} m" else "%.1f km".format(meters / 1000.0)

    private fun buildPoiLine(pick: PoiNearby, rider: RiderLocation?, statusColor: Int): CharSequence {
        val arrow = directionArrow(pick, rider)
        val tail = "${formatDistance(pick.distanceMeters)}  ${pick.poi.name}"
        val full = if (arrow == null) tail else "$arrow $tail"
        return SpannableString(full).apply {
            setSpan(
                ForegroundColorSpan(statusColor),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (arrow != null) {
                setSpan(
                    RelativeSizeSpan(ARROW_SCALE),
                    0,
                    1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(ARROW_COLOR),
                    0,
                    1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun directionArrow(pick: PoiNearby, rider: RiderLocation?): Char? {
        val heading = rider?.orientationDegrees ?: return null
        val bearingToPoi = Geo.bearingDegrees(rider.point, LatLng(pick.poi.lat, pick.poi.lon))
        return arrowFor(bearingToPoi - heading)
    }

    companion object {
        private val ARROW_COLOR = Color.parseColor("#3B9EFF")
        private val COLOR_OPEN = Color.parseColor("#5BE584")
        private val COLOR_UNKNOWN = Color.parseColor("#FFB74D")
        private val COLOR_CLOSED = Color.parseColor("#FF6B6B")
        private val COLOR_DEFAULT = Color.parseColor("#FFFFFF")
        private const val ARROW_SCALE = 1.5f
        const val TYPE_RESTAURANT = "nearby_restaurant"
        const val TYPE_SUPERMARKET = "nearby_supermarket"
        const val TYPE_FUEL = "nearby_fuel"
        const val TYPE_CAFE = "nearby_cafe"
        const val TYPE_HOTEL = "nearby_hotel"
        const val TYPE_DOCTOR = "nearby_doctor"
        const val TYPE_PHARMACY = "nearby_pharmacy"
        const val TYPE_BIKE_SHOP = "nearby_bike_shop"
        const val TYPE_ATM = "nearby_atm"
        const val TYPE_TRAIN_STATION = "nearby_train_station"
        const val TYPE_WATER_REFILL = "nearby_water_refill"
        private const val SAMPLE_MS = 10_000L
    }
}

internal fun placeholderFor(state: RouteFetchState): Int =
    if (state is RouteFetchState.Error) R.string.field_waiting_for_wifi else R.string.field_none

/**
 * Tile-side filter on top of [computeNearbyPicks]. Picks the first match by category, and drops it
 * if it is farther than [maxDistanceMeters]. The distance gate is what keeps a stale cache from a
 * different region (e.g. a prior ride in another city) from being served as if it were relevant to
 * the current ride — see issue #149. Threshold sits above the corridor-fetch radius (10 km) with a
 * margin for short off-route deviations.
 */
internal fun tilePick(
    picks: List<PoiNearby>,
    category: PoiCategory,
    maxDistanceMeters: Double = MAX_PICK_DISTANCE_METERS,
): PoiNearby? =
    picks.firstOrNull { it.poi.category == category }
        ?.takeIf { it.distanceMeters <= maxDistanceMeters }

internal const val MAX_PICK_DISTANCE_METERS: Double = 15_000.0
