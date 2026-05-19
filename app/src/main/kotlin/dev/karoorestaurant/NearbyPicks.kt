package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal fun computeNearbyPicks(karoo: KarooClient, center: LatLng): List<PoiNearby> {
    val nowInstant = Instant.now()
    val nowLdt = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault())
    val store = karoo.store()
    return PoiCategory.values().mapNotNull { category ->
        val candidates = store.nearest(center, category, maxMeters = 30_000.0, limit = 50, now = nowInstant)
        candidates.firstOrNull()?.let { hit ->
            PoiNearby(
                poi = hit.poi,
                distanceMeters = hit.distanceMeters,
                status = OpeningHours.evaluate(hit.poi.openingHoursTag, nowLdt),
                staleness = stalenessOf(hit.fetchedAt, nowInstant),
                isFavorite = hit.isFavorite,
            )
        }
    }
}
