package dev.karoorestaurant

import dev.karoorestaurant.data.overpass.OverpassClient
import dev.karoorestaurant.data.overpass.OverpassFetcher
import dev.karoorestaurant.data.overpass.OverpassMapper
import dev.karoorestaurant.data.overpass.OverpassQueryBuilder
import dev.karoorestaurant.data.overpass.OverpassResponse
import dev.karoorestaurant.data.overpass.dedupAcrossWindows
import dev.karoorestaurant.data.poi.Poi
import dev.karoorestaurant.data.route.LatLng
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [OverpassFetcher] that routes HTTP through the karoo-ext bridge so traffic uses the
 * Karoo's best available connection (Wi-Fi if connected, otherwise the Bluetooth-tethered
 * phone). Direct OkHttp calls cannot reach the internet on a real Karoo without a SIM.
 *
 * Each window is one bridged POST. Transient 429 responses are retried with exponential
 * backoff up to [maxRetries]. Each request is wrapped in a [withTimeout] guard that
 * cancels the consumer on expiration, freeing the underlying listener.
 *
 * Adapted from wilfredstegeman/karoo-http-bridge fork (feat/karoo-http-bridge, ba57753),
 * keeping the route-driven prefetch model.
 */
class KarooOverpassFetcher(
    private val systemPort: KarooSystemPort,
    private val endpoint: String = OverpassClient.DEFAULT_ENDPOINT,
    private val maxRetries: Int = OverpassClient.DEFAULT_MAX_RETRIES,
    private val baseBackoffMs: Long = OverpassClient.DEFAULT_BASE_BACKOFF_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : OverpassFetcher {

    override suspend fun invoke(windows: List<List<LatLng>>, radiusMeters: Int): List<Poi> =
        dedupAcrossWindows(windows) { samples -> fetchWindow(samples, radiusMeters) }

    private suspend fun fetchWindow(samples: List<LatLng>, radiusMeters: Int): List<Poi> {
        val query = OverpassQueryBuilder.build(samples, radiusMeters)
        val bodyForm = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        val bodyBytes = bodyForm.toByteArray(Charsets.UTF_8)
        check(bodyBytes.size <= OnHttpResponse.MAX_REQUEST_SIZE) {
            "Overpass request body ${bodyBytes.size} bytes exceeds bridge limit ${OnHttpResponse.MAX_REQUEST_SIZE}"
        }

        var attempt = 0
        while (true) {
            val complete = withTimeout(timeoutMs) { sendOnce(bodyBytes) }
            if (complete.error != null) {
                error("Overpass bridge error: ${complete.error}")
            }
            when (complete.statusCode) {
                429 -> {
                    if (attempt >= maxRetries) error("Overpass 429 after $maxRetries retries")
                }
                in 200..299 -> {
                    val payloadBytes = complete.body ?: error("empty Overpass response")
                    val payload = String(payloadBytes, Charsets.UTF_8)
                    val parsed = json.decodeFromString(OverpassResponse.serializer(), payload)
                    return parsed.elements
                        .mapNotNull(OverpassMapper::toPoi)
                        .distinctBy { "${it.osmType}/${it.osmId}" }
                }
                else -> error("Overpass HTTP ${complete.statusCode}")
            }
            delay(backoffMs(attempt))
            attempt++
        }
    }

    private suspend fun sendOnce(bodyBytes: ByteArray): HttpResponseState.Complete =
        suspendCancellableCoroutine { cont ->
            val params = OnHttpResponse.MakeHttpRequest(
                method = "POST",
                url = endpoint,
                headers = mapOf(
                    "User-Agent" to "karoo-restaurant/0.2",
                    "Content-Type" to "application/x-www-form-urlencoded",
                ),
                body = bodyBytes,
                waitForConnection = true,
            )
            val consumerId = systemPort.makeHttpRequest(
                params = params,
                onError = { msg ->
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(msg))
                },
                onComplete = { /* listener auto-removed by SDK after Complete */ },
                onEvent = { event ->
                    val state = event.state
                    if (state is HttpResponseState.Complete && cont.isActive) {
                        cont.resume(state)
                    }
                },
            )
            cont.invokeOnCancellation { systemPort.removeConsumer(consumerId) }
        }

    private fun backoffMs(attempt: Int): Long {
        val multiplier = 1L shl attempt.coerceAtMost(20)
        return (baseBackoffMs * multiplier).coerceAtMost(OverpassClient.MAX_BACKOFF_MS)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 60_000L
    }
}
