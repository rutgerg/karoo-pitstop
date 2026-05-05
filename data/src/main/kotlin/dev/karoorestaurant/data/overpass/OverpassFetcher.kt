package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng

/**
 * Contract shared by every transport that can pull Overpass POIs for a corridor.
 *
 * Each window is one Overpass `around:` query. Implementations are expected to run
 * windows sequentially, dedupe across windows by `osm_type/osm_id`, and surface a
 * single failed window as a thrown error (no partial results).
 *
 * The single production implementation is `OverpassClient`, which uses OkHttp. It runs
 * unchanged on the JVM CLI (`:data:run`), the Android app on a Karoo 3, and tests.
 */
fun interface OverpassFetcher {
    suspend operator fun invoke(
        windows: List<List<LatLng>>,
        radiusMeters: Int,
        categories: Set<PoiCategory>?,
    ): List<Poi>
}

/**
 * Run the per-window [fetchOne] sequentially across [windows] and dedupe results by
 * `osm_type/osm_id`. Used by `OverpassClient`.
 */
suspend fun dedupAcrossWindows(
    windows: List<List<LatLng>>,
    fetchOne: suspend (List<LatLng>) -> List<Poi>,
): List<Poi> {
    require(windows.isNotEmpty()) { "windows must not be empty" }
    require(windows.all { it.isNotEmpty() }) { "every window must have at least one sample" }
    val seen = LinkedHashMap<String, Poi>()
    for (window in windows) {
        for (poi in fetchOne(window)) {
            seen.putIfAbsent("${poi.osmType}/${poi.osmId}", poi)
        }
    }
    return seen.values.toList()
}
