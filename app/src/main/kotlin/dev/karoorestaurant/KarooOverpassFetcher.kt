package dev.karoorestaurant

import dev.karoorestaurant.data.overpass.OverpassFetcher
import dev.karoorestaurant.data.overpass.OverpassQueryBuilder
import dev.karoorestaurant.data.overpass.OverpassResponse
import dev.karoorestaurant.data.overpass.toPoi
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.route.LatLng
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class KarooOverpassFetcher(
    private val karooSystem: KarooSystemService,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OverpassFetcher {

    override suspend fun fetchCorridor(
        samples: List<LatLng>,
        radiusMeters: Int,
    ): List<Poi> {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        val query = OverpassQueryBuilder.build(samples, radiusMeters)
        val body = "data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8)

        return withTimeout(REQUEST_TIMEOUT) {
            suspendCancellableCoroutine<List<Poi>> { cont ->
                var listenerId = ""
                listenerId = karooSystem.addConsumer<OnHttpResponse>(
                    params = OnHttpResponse.MakeHttpRequest(
                        method = "POST",
                        url = endpoint,
                        headers = mapOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "User-Agent" to "karoo-restaurant/0.1 (karoo)",
                        ),
                        body = body,
                        waitForConnection = true,
                    ),
                    onError = { msg ->
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Overpass bridge error: $msg"),
                            )
                        }
                    },
                ) { event: OnHttpResponse ->
                    when (val state = event.state) {
                        is HttpResponseState.Queued, is HttpResponseState.InProgress -> Unit
                        is HttpResponseState.Complete -> {
                            karooSystem.removeConsumer(listenerId)
                            if (cont.isActive) {
                                handleComplete(state, cont::resume, cont::resumeWithException)
                            }
                        }
                    }
                }
                cont.invokeOnCancellation {
                    karooSystem.removeConsumer(listenerId)
                }
            }
        }
    }

    private fun handleComplete(
        state: HttpResponseState.Complete,
        resumeOk: (List<Poi>) -> Unit,
        resumeErr: (Throwable) -> Unit,
    ) {
        try {
            when {
                state.error != null -> resumeErr(
                    IllegalStateException("Overpass bridge error: ${state.error}"),
                )
                state.statusCode !in 200..299 -> {
                    val preview = state.body?.decodeToString()?.take(200).orEmpty()
                    resumeErr(IllegalStateException("Overpass HTTP ${state.statusCode}: $preview"))
                }
                else -> {
                    val payload = state.body?.decodeToString()
                        ?: throw IllegalStateException("empty Overpass response")
                    val parsed = json.decodeFromString(OverpassResponse.serializer(), payload)
                    val pois = parsed.elements
                        .mapNotNull { it.toPoi() }
                        .distinctBy { "${it.osmType}/${it.osmId}" }
                    resumeOk(pois)
                }
            }
        } catch (t: Throwable) {
            resumeErr(t)
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
        private val REQUEST_TIMEOUT = 60.seconds
    }
}
