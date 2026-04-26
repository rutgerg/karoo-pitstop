package dev.karoorestaurant.data.route

object CorridorSlicer {

    fun sample(polyline: List<LatLng>, stepMeters: Double = 2_000.0): List<LatLng> {
        require(stepMeters > 0) { "stepMeters must be positive" }
        if (polyline.isEmpty()) return emptyList()
        if (polyline.size == 1) return listOf(polyline.first())

        val out = mutableListOf(polyline.first())
        var carry = 0.0
        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segment = Geo.haversineMeters(a, b)
            if (segment == 0.0) continue
            var distAlong = stepMeters - carry
            while (distAlong < segment) {
                val t = distAlong / segment
                out += LatLng(
                    lat = a.lat + (b.lat - a.lat) * t,
                    lon = a.lon + (b.lon - a.lon) * t,
                )
                distAlong += stepMeters
            }
            carry = (carry + segment) % stepMeters
        }
        if (out.last() != polyline.last()) out += polyline.last()
        return out
    }
}
