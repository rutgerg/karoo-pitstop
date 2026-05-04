package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory

object OverpassMapper {
    fun toPoi(element: OverpassElement): Poi? {
        val lat = element.effectiveLat ?: return null
        val lon = element.effectiveLon ?: return null
        val name = element.tags["name"] ?: return null
        val category = PoiCategory.fromTags(element.tags) ?: return null
        return Poi(
            osmId = element.id,
            osmType = element.type,
            name = name,
            category = category,
            lat = lat,
            lon = lon,
            openingHoursTag = element.tags["opening_hours"],
        )
    }
}
