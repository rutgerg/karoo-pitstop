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

/**
 * OkHttp-based [OverpassFetcher]. Used by the JVM CLI prototype (`:data:run`), the
 * `:app` Android module on a Karoo 3, and unit tests. On the device this works because
 * the Karoo connects to Wi-Fi (home or phone hotspot) and the app holds the INTERNET
 * permission, so OkHttp routes through the active default network just like any other
 * Android app.
 */
class OverpassClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val http: OkHttpClient = defaultHttp(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
) : OverpassFetcher {

    override suspend fun invoke(
        windows: List<List<LatLng>>,
        radiusMeters: Int,
        categories: Set<PoiCategory>?,
    ): List<Poi> =
        withContext(Dispatchers.IO) {
            dedupAcrossWindows(windows) { samples -> fetchWindow(samples, radiusMeters, categories) }
        }

    /** Backwards-compatible name for the CLI prototype; delegates to [invoke]. */
    suspend fun fetchCorridor(windows: List<List<LatLng>>, radiusMeters: Int = 10_000): List<Poi> =
        invoke(windows, radiusMeters, null)

    private suspend fun fetchWindow(
        samples: List<LatLng>,
        radiusMeters: Int,
        categories: Set<PoiCategory>?,
    ): List<Poi> {
        val body = FormBody.Builder()
            .add("data", OverpassQueryBuilder.build(samples, radiusMeters, categories))
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
                    response.body?.close()
                } else {
                    if (!response.isSuccessful) {
                        error("Overpass HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                    }
                    val payload = response.body?.string() ?: error("empty Overpass response")
                    val parsed = json.decodeFromString(OverpassResponse.serializer(), payload)
                    return parsed.elements
                        .mapNotNull(OverpassMapper::toPoi)
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
