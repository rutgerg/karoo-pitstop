package dev.karoorestaurant

import io.hammerhead.karooext.models.KarooEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState

class FakeKarooSystemPort : KarooSystemPort {

    val dispatched: MutableList<KarooEffect> = mutableListOf()
    val locationHandlers = mutableMapOf<String, (OnLocationChanged) -> Unit>()
    val navHandlers = mutableMapOf<String, (OnNavigationState) -> Unit>()
    private var consumerSeq = 0
    var connected: Boolean = false
        private set

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

    override fun removeConsumer(id: String) {
        locationHandlers.remove(id)
        navHandlers.remove(id)
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
