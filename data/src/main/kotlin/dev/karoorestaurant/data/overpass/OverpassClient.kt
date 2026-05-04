package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.poi.PoiCategory
import dev.karoorestaurant.data.route.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OverpassClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val http: OkHttpClient = defaultHttp(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
) {

    /**
     * Fetch all POIs across [windows] sequentially and dedupe by `osm_type/osm_id`.
     *
     * Each window is one Overpass `around:` query. Splitting a long route over multiple
     * windows keeps each request inside the server's per-query timeout and reduces 429s.
     * Transient 429 responses are retried with exponential backoff up to [maxRetries].
     */
    suspend fun fetchCorridor(
        windows: List<List<LatLng>>,
        radiusMeters: Int = 10_000,
    ): List<Poi> = withContext(Dispatchers.IO) {
        require(windows.isNotEmpty()) { "windows must not be empty" }
        require(windows.all { it.isNotEmpty() }) { "every window must have at least one sample" }

        val seen = LinkedHashMap<String, Poi>()
        for (window in windows) {
            val pois = fetchWindow(window, radiusMeters)
            for (poi in pois) {
                seen.putIfAbsent("${poi.osmType}/${poi.osmId}", poi)
            }
        }
        seen.values.toList()
    }

    private suspend fun fetchWindow(samples: List<LatLng>, radiusMeters: Int): List<Poi> {
        val body = FormBody.Builder()
            .add("data", buildQuery(samples, radiusMeters))
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("User-Agent", "karoo-restaurant/0.2 (data prototype)")
            .build()

        var attempt = 0
        while (true) {
            val response = http.newCall(request).execute()
            try {
                if (response.code == 429) {
                    if (attempt >= maxRetries) {
                        error("Overpass 429 after $maxRetries retries")
                    }
                    // Drain body before delay so the connection can be reused.
                    response.body?.close()
                } else {
                    if (!response.isSuccessful) {
                        error("Overpass HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                    }
                    val payload = response.body?.string() ?: error("empty Overpass response")
                    val parsed = json.decodeFromString(OverpassResponse.serializer(), payload)
                    return parsed.elements
                        .mapNotNull { it.toPoi() }
                        .distinctBy { "${it.osmType}/${it.osmId}" }
                }
            } finally {
                response.close()
            }
            delay(backoffMs(attempt))
            attempt++
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val multiplier = 1L shl attempt.coerceAtMost(20)
        return (baseBackoffMs * multiplier).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun OverpassElement.toPoi(): Poi? {
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

    private fun buildQuery(samples: List<LatLng>, radiusMeters: Int): String {
        val coords = samples.joinToString(",") {
            "${"%.6f".format(java.util.Locale.US, it.lat)},${"%.6f".format(java.util.Locale.US, it.lon)}"
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

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 16_000L

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
