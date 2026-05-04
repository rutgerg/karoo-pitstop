package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.LocalDateTime

internal fun computeNearbyPicks(karoo: KarooClient, center: LatLng): List<PoiNearby> {
    val now = LocalDateTime.now()
    val store = karoo.store()
    return PoiCategory.values().mapNotNull { category ->
        val candidates = store.nearest(center, category, maxMeters = 30_000.0, limit = 50)
        candidates.firstNotNullOfOrNull { (poi, dist) ->
            val status = OpeningHours.evaluate(poi.openingHoursTag, now)
            if (status is OpeningHours.Status.Closed) null else PoiNearby(poi, dist, status)
        }
    }
}
