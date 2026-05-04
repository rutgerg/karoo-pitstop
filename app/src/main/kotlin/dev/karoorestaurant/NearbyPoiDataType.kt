package dev.karoorestaurant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
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
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        emitter.setCancellable { scope.cancel() }

        emitter.updateView(buildView(context, pick = null, placeholderRes = R.string.field_waiting))

        scope.launch {
            karoo.locationFlow.sample(SAMPLE_MS).collect { location ->
                val pick = withContext(Dispatchers.IO) {
                    computeNearbyPicks(karoo, location).firstOrNull { it.poi.category == category }
                }
                emitter.updateView(buildView(context, pick = pick, placeholderRes = R.string.field_none))
            }
        }
    }

    private fun buildView(context: Context, pick: PoiNearby?, placeholderRes: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.data_field_nearby_poi)
        val mainText = pick?.let { "${formatDistance(it.distanceMeters)}  ${it.poi.name}" }
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
        val openLabel = context.getString(R.string.hours_open_prefix)
        val hoursPart: String? = when (pick.status) {
            OpeningHours.Status.Open -> {
                val tag = pick.poi.openingHoursTag
                if (tag.isNullOrBlank()) openLabel else "$openLabel: $tag"
            }
            is OpeningHours.Status.Unknown -> "$openLabel: ${context.getString(R.string.hours_unknown)}"
            OpeningHours.Status.Closed -> null
        }
        val unverified = if (pick.staleness == Staleness.AGING) "unverified" else null
        return listOfNotNull(hoursPart, unverified).joinToString(" · ").ifBlank { null }
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
