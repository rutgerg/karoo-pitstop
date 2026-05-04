package dev.karoorestaurant

import io.hammerhead.karooext.models.KarooEffect
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState

class FakeKarooSystemPort : KarooSystemPort {

    val dispatched: MutableList<KarooEffect> = mutableListOf()
    val locationHandlers = mutableMapOf<String, (OnLocationChanged) -> Unit>()
    val navHandlers = mutableMapOf<String, (OnNavigationState) -> Unit>()
    val httpRequests: MutableList<OnHttpResponse.MakeHttpRequest> = mutableListOf()
    val httpHandlers = mutableMapOf<String, HttpHandler>()
    private var consumerSeq = 0
    var connected: Boolean = false
        private set

    data class HttpHandler(
        val onError: (String) -> Unit,
        val onComplete: () -> Unit,
        val onEvent: (OnHttpResponse) -> Unit,
    )

    override fun connect(callback: (Boolean) -> Unit) {
        connected = true
        callback(true)
    }

    override fun disconnect() {
        connected = false
    }

    override fun observeLocations(handler: (OnLocationChanged) -> Unit): String {
        val id = "loc-${++consumerSeq}"
        locationHandlers[id] = handler
        return id
    }

    override fun observeNavigationStates(handler: (OnNavigationState) -> Unit): String {
        val id = "nav-${++consumerSeq}"
        navHandlers[id] = handler
        return id
    }

    override fun makeHttpRequest(
        params: OnHttpResponse.MakeHttpRequest,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        onEvent: (OnHttpResponse) -> Unit,
    ): String {
        httpRequests.add(params)
        val id = "http-${++consumerSeq}"
        httpHandlers[id] = HttpHandler(onError, onComplete, onEvent)
        return id
    }

    /** Fire a Complete event for the most recent (still-active) HTTP request. */
    fun completeLatestHttp(statusCode: Int, body: ByteArray? = null, error: String? = null) {
        val (_, handler) = httpHandlers.entries.last()
        handler.onEvent(OnHttpResponse(io.hammerhead.karooext.models.HttpResponseState.Complete(
            statusCode = statusCode,
            headers = emptyMap(),
            body = body,
            error = error,
        )))
    }

    override fun removeConsumer(id: String) {
        locationHandlers.remove(id)
        navHandlers.remove(id)
        httpHandlers.remove(id)
    }

    override fun dispatch(effect: KarooEffect): Boolean {
        dispatched.add(effect)
        return connected
    }

    fun emitLocation(event: OnLocationChanged) {
        locationHandlers.values.forEach { it(event) }
    }

    fun emitNavigationState(event: OnNavigationState) {
        navHandlers.values.forEach { it(event) }
    }
}
