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
) : DataTypeImpl(extension = RestaurantExtensionService.EXTENSION_ID, typeId = typeId) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        (context.applicationContext as? KarooRestaurantApp)?.telemetry?.recordTileRender()

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        emitter.setCancellable { scope.cancel() }

        emitter.updateView(buildView(context, pick = null, rider = null, placeholderRes = R.string.field_waiting))

        scope.launch {
            combine(
                karoo.locationFlow.sample(SAMPLE_MS),
                routeFetchState,
            ) { rider, state -> rider to state }.collect { (rider, state) ->
                val pick = withContext(Dispatchers.IO) {
                    tilePick(computeNearbyPicks(karoo, rider.point), category)
                }
                emitter.updateView(
                    buildView(context, pick = pick, rider = rider, placeholderRes = placeholderFor(state)),
                )
            }
        }
    }

    private fun buildView(
        context: Context,
        pick: PoiNearby?,
        rider: RiderLocation?,
        placeholderRes: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.data_field_nearby_poi)
        val mainText = pick?.let { buildPoiLine(it, rider) }
            ?: context.getString(placeholderRes)
        views.setTextViewText(R.id.poi_text, mainText)

        val hoursText = pick?.let { formatHours(context, it) }
        if (hoursText.isNullOrBlank()) {
            views.setViewVisibility(R.id.poi_hours, View.GONE)
        } else {
            views.setTextViewText(R.id.poi_hours, hoursText)
            views.setViewVisibility(R.id.poi_hours, View.VISIBLE)
        }

        views.setOnClickPendingIntent(R.id.poi_root, pick?.let { buildLaunchPendingIntent(context, it.poi) })
        return views
    }

    private fun formatHours(context: Context, pick: PoiNearby): String? {
        val statusWord = when (pick.status) {
            OpeningHours.Status.Open -> context.getString(R.string.hours_open_prefix)
            OpeningHours.Status.Closed -> context.getString(R.string.hours_closed)
            is OpeningHours.Status.Unknown -> context.getString(R.string.hours_unknown)
        }
        val hoursPart = "${context.getString(R.string.status_prefix)}: $statusWord"
        val unverified = if (pick.staleness == Staleness.AGING) "unverified" else null
        return listOfNotNull(hoursPart, unverified).joinToString(" · ")
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

    private fun buildPoiLine(pick: PoiNearby, rider: RiderLocation?): CharSequence {
        val arrow = directionArrow(pick, rider)
        val tail = "${formatDistance(pick.distanceMeters)}  ${pick.poi.name}"
        if (arrow == null) return tail
        val full = "$arrow $tail"
        return SpannableString(full).apply {
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

    private fun directionArrow(pick: PoiNearby, rider: RiderLocation?): Char? {
        val heading = rider?.orientationDegrees ?: return null
        val bearingToPoi = Geo.bearingDegrees(rider.point, LatLng(pick.poi.lat, pick.poi.lon))
        return arrowFor(bearingToPoi - heading)
    }

    companion object {
        private val ARROW_COLOR = Color.parseColor("#3B9EFF")
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
