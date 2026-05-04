package dev.karoorestaurant.data.route

object CorridorSlicer {

    const val DEFAULT_STEP_METERS: Double = 2_000.0
    const val DEFAULT_WINDOW_METERS: Double = 50_000.0

    fun sample(polyline: List<LatLng>, stepMeters: Double = DEFAULT_STEP_METERS): List<LatLng> {
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

    /**
     * Slice a polyline into windows of approximately [windowMeters] each, with each window
     * carrying its own list of step samples. Useful for splitting an Overpass `around:` query
     * over a long route to stay under per-request timeouts and rate limits.
     *
     * Returned windows do not overlap. Coverage continuity at window boundaries comes from
     * the radius around each sample (typically 10 km), not from window-side overlap.
     */
    fun windows(
        polyline: List<LatLng>,
        windowMeters: Double = DEFAULT_WINDOW_METERS,
        stepMeters: Double = DEFAULT_STEP_METERS,
    ): List<List<LatLng>> {
        require(windowMeters > 0) { "windowMeters must be positive" }
        require(stepMeters > 0) { "stepMeters must be positive" }
        val samples = sample(polyline, stepMeters)
        if (samples.isEmpty()) return emptyList()
        val perWindow = (windowMeters / stepMeters).toInt().coerceAtLeast(1)
        return samples.chunked(perWindow)
    }
}
