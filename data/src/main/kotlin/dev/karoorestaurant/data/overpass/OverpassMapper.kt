package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory

fun OverpassElement.toPoi(): Poi? {
    val lat = effectiveLat ?: return null
    val lon = effectiveLon ?: return null
    val name = tags["name"] ?: return null
    val category = PoiCategory.fromTags(tags) ?: return null
    return Poi(
        osmId = id,
        osmType = type,
        name = name,
        category = category,
        lat = lat,
        lon = lon,
        openingHoursTag = tags["opening_hours"],
    )
}
