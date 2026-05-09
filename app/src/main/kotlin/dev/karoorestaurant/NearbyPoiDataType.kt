package dev.karoorestaurant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class NearbyPoiDataType(
    private val karoo: KarooClient,
    private val category: PoiCategory,
    typeId: String,
) : DataTypeImpl(extension = RestaurantExtensionService.EXTENSION_ID, typeId = typeId) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        (context.applicationContext as? KarooRestaurantApp)?.telemetry?.recordTileRender()

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        emitter.setCancellable { scope.cancel() }

        emitter.updateView(buildView(context, pick = null, rider = null, placeholderRes = R.string.field_waiting))

        scope.launch {
            karoo.locationFlow.sample(SAMPLE_MS).collect { rider ->
                val pick = withContext(Dispatchers.IO) {
                    computeNearbyPicks(karoo, rider.point).firstOrNull { it.poi.category == category }
                }
                emitter.updateView(buildView(context, pick = pick, rider = rider, placeholderRes = R.string.field_none))
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

        // Spike for issue #63: route tile tap to BrowsePoisActivity to verify
        // PendingIntent.getActivity is honored from a RemoteViews click on Karoo.
        views.setOnClickPendingIntent(R.id.poi_root, buildBrowsePendingIntent(context))
        return views
    }

    private fun buildBrowsePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BrowsePoisActivity::class.java).apply {
            putExtra(BrowsePoisActivity.EXTRA_CATEGORY, category.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            category.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatHours(context: Context, pick: PoiNearby): String? {
        val hoursPart: String = when (pick.status) {
            OpeningHours.Status.Open -> context.getString(R.string.hours_open_prefix)
            OpeningHours.Status.Closed -> context.getString(R.string.hours_closed)
            is OpeningHours.Status.Unknown -> context.getString(R.string.hours_unknown)
        }
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

    private fun buildPoiLine(pick: PoiNearby, rider: RiderLocation?): String {
        val arrow = directionArrow(pick, rider)
        val prefix = if (arrow != null) "$arrow " else ""
        return "$prefix${formatDistance(pick.distanceMeters)}  ${pick.poi.name}"
    }

    private fun directionArrow(pick: PoiNearby, rider: RiderLocation?): Char? {
        val heading = rider?.orientationDegrees ?: return null
        val bearingToPoi = Geo.bearingDegrees(rider.point, LatLng(pick.poi.lat, pick.poi.lon))
        return arrowFor(bearingToPoi - heading)
    }

    companion object {
        const val TYPE_RESTAURANT = "nearby_restaurant"
        const val TYPE_SUPERMARKET = "nearby_supermarket"
        const val TYPE_FUEL = "nearby_fuel"
        const val TYPE_CAFE = "nearby_cafe"
        const val TYPE_HOTEL = "nearby_hotel"
        const val TYPE_DOCTOR = "nearby_doctor"
        const val TYPE_PHARMACY = "nearby_pharmacy"
        const val TYPE_BIKE_SHOP = "nearby_bike_shop"
        const val TYPE_ATM = "nearby_atm"
        private const val SAMPLE_MS = 10_000L
    }
}
