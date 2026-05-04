package dev.karoorestaurant

import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheStateNotifierTest {

    private fun newNotifier(port: KarooSystemPort, scope: CoroutineScope) = CacheStateNotifier(
        systemPort = port,
        header = "Pitstop",
        successFormat = { count, name -> "$count POIs cached for $name" },
        failureMessage = "Prefetch failed — check phone connection",
        scope = scope,
    )

    @Test
    fun `Cached transition dispatches EVENT notification`() = runTest {
        val port = FakeKarooSystemPort()
        val state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        newNotifier(port, scope).observe(state)

        state.value = RouteFetchState.Cached("Granada Loop", 5068)

        assertEquals(1, port.dispatched.size)
        val n = port.dispatched.single() as SystemNotification
        assertEquals(CacheStateNotifier.NOTIFICATION_ID, n.id)
        assertEquals("Pitstop", n.header)
        assertEquals("5068 POIs cached for Granada Loop", n.message)
        assertEquals(SystemNotification.Style.EVENT, n.style)
    }

    @Test
    fun `Error transition dispatches ERROR notification with message as subText`() = runTest {
        val port = FakeKarooSystemPort()
        val state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        newNotifier(port, scope).observe(state)

        state.value = RouteFetchState.Error("Overpass HTTP 504")

        val n = port.dispatched.single() as SystemNotification
        assertEquals(SystemNotification.Style.ERROR, n.style)
        assertEquals("Prefetch failed — check phone connection", n.message)
        assertEquals("Overpass HTTP 504", n.subText)
    }

    @Test
    fun `Idle and Fetching transitions are silent`() = runTest {
        val port = FakeKarooSystemPort()
        val state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        newNotifier(port, scope).observe(state)

        state.value = RouteFetchState.Fetching("Some Route")
        state.value = RouteFetchState.Idle

        assertTrue(port.dispatched.isEmpty(), "expected no dispatch for Idle / Fetching")
    }

    @Test
    fun `subsequent Cached updates reuse the same notification id`() = runTest {
        val port = FakeKarooSystemPort()
        val state = MutableStateFlow<RouteFetchState>(RouteFetchState.Idle)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        newNotifier(port, scope).observe(state)

        state.value = RouteFetchState.Cached("Route A", 100)
        state.value = RouteFetchState.Cached("Route B", 200)

        assertEquals(2, port.dispatched.size)
        val ids = port.dispatched.map { (it as SystemNotification).id }.toSet()
        assertEquals(setOf(CacheStateNotifier.NOTIFICATION_ID), ids)
    }
}
