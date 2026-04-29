package dev.karoorestaurant.data.overpass

import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.route.LatLng
import kotlinx.coroutines.Dispatchers
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
) : OverpassFetcher {

    override suspend fun fetchCorridor(
        samples: List<LatLng>,
        radiusMeters: Int,
    ): List<Poi> = withContext(Dispatchers.IO) {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        val body = FormBody.Builder()
            .add("data", OverpassQueryBuilder.build(samples, radiusMeters))
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .header("User-Agent", "karoo-restaurant/0.1 (data prototype)")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Overpass HTTP ${response.code}: ${response.body?.string()?.take(200)}")
            }
            val payload = response.body?.string() ?: error("empty Overpass response")
            val parsed = json.decodeFromString(OverpassResponse.serializer(), payload)
            parsed.elements
                .mapNotNull { it.toPoi() }
                .distinctBy { "${it.osmType}/${it.osmId}" }
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
