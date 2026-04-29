package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.route.LatLng
import java.util.Locale

object OverpassQueryBuilder {

    fun build(samples: List<LatLng>, radiusMeters: Int): String {
        val coords = samples.joinToString(",") {
            "${"%.6f".format(Locale.US, it.lat)},${"%.6f".format(Locale.US, it.lon)}"
        }
        return """
            [out:json][timeout:60];
            (
              nwr["amenity"="restaurant"](around:$radiusMeters,$coords);
              nwr["amenity"="fuel"](around:$radiusMeters,$coords);
              nwr["shop"~"^(supermarket|convenience)${'$'}"](around:$radiusMeters,$coords);
            );
            out center tags;
        """.trimIndent()
    }
}
