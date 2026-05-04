package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.route.LatLng

object OverpassQueryBuilder {
    fun build(samples: List<LatLng>, radiusMeters: Int): String {
        val coords = samples.joinToString(",") {
            "${"%.6f".format(java.util.Locale.US, it.lat)},${"%.6f".format(java.util.Locale.US, it.lon)}"
        }
        return """
            [out:json][timeout:60];
            (
              nwr["amenity"="restaurant"](around:$radiusMeters,$coords);
              nwr["amenity"="fuel"](around:$radiusMeters,$coords);
              nwr["shop"~"^(supermarket|convenience)${'$'}"](around:$radiusMeters,$coords);
              nwr["amenity"~"^(bar|cafe)${'$'}"](around:$radiusMeters,$coords);
              nwr["tourism"~"^(hotel|guest_house|hostel|motel)${'$'}"](around:$radiusMeters,$coords);
              nwr["amenity"~"^(doctors|clinic)${'$'}"](around:$radiusMeters,$coords);
              nwr["amenity"="pharmacy"](around:$radiusMeters,$coords);
              nwr["shop"="bicycle"](around:$radiusMeters,$coords);
              nwr["amenity"="atm"](around:$radiusMeters,$coords);
            );
            out center tags;
        """.trimIndent()
    }
}
