package dev.karoorestaurant

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEffect
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState

interface KarooSystemPort {
    fun connect(callback: (Boolean) -> Unit)
    fun disconnect()
    fun observeLocations(handler: (OnLocationChanged) -> Unit): String
    fun observeNavigationStates(handler: (OnNavigationState) -> Unit): String
    fun makeHttpRequest(
        params: OnHttpResponse.MakeHttpRequest,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        onEvent: (OnHttpResponse) -> Unit,
    ): String
    fun removeConsumer(id: String)
    fun dispatch(effect: KarooEffect): Boolean
}

class RealKarooSystemPort(context: Context) : KarooSystemPort {
    private val service = KarooSystemService(context.applicationContext)
    override fun connect(callback: (Boolean) -> Unit) = service.connect(callback)
    override fun disconnect() = service.disconnect()
    override fun observeLocations(handler: (OnLocationChanged) -> Unit): String =
        service.addConsumer<OnLocationChanged> { handler(it) }
    override fun observeNavigationStates(handler: (OnNavigationState) -> Unit): String =
        service.addConsumer<OnNavigationState> { handler(it) }
    override fun makeHttpRequest(
        params: OnHttpResponse.MakeHttpRequest,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        onEvent: (OnHttpResponse) -> Unit,
    ): String = service.addConsumer<OnHttpResponse>(
        params = params,
        onError = onError,
        onComplete = onComplete,
        onEvent = onEvent,
    )
    override fun removeConsumer(id: String) = service.removeConsumer(id)
    override fun dispatch(effect: KarooEffect): Boolean = service.dispatch(effect)
}
