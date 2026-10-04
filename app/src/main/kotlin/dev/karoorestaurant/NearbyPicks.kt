package dev.karoorestaurant

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One pick per category: the nearest candidate that passes the Show Closed POIs filter and has
 * not been skipped from the tile. When every eligible candidate is skipped the pick wraps back to
 * the nearest one, so a wrong skip is recoverable by skipping again (see [SkipStore.skip]).
 */
internal fun computeNearbyPicks(
    karoo: KarooClient,
    center: LatLng,
    showClosed: Boolean = true,
    skipped: Map<PoiCategory, Set<Long>> = emptyMap(),
): List<PoiNearby> {
    val nowInstant = Instant.now()
    val nowLdt = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault())
    val store = karoo.store()
    return PoiCategory.values().mapNotNull { category ->
        val candidates = store.nearest(center, category, maxMeters = 30_000.0, limit = 50, now = nowInstant)
        val eligible = candidates.asSequence()
            .map { hit ->
                PoiNearby(
                    poi = hit.poi,
                    distanceMeters = hit.distanceMeters,
                    status = OpeningHours.evaluate(hit.poi.openingHoursTag, nowLdt),
                    staleness = stalenessOf(hit.fetchedAt, nowInstant),
                )
            }
            .filter { showClosed || it.status != OpeningHours.Status.Closed }
            .mapIndexed { index, pick -> pick.copy(rank = index) }
            .toList()
        val skippedIds = skipped[category].orEmpty()
        eligible.firstOrNull { it.poi.osmId !in skippedIds } ?: eligible.firstOrNull()
    }
}
