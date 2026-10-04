package dev.karoorestaurant

import dev.karoorestaurant.data.poi.PoiCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkipStoreTest {

    @Test
    fun `starts with nothing skipped`() {
        assertTrue(SkipStore().skipped.value.isEmpty())
    }

    @Test
    fun `skip accumulates ids per category`() {
        val store = SkipStore()
        store.skip(PoiCategory.RESTAURANT, 1L)
        store.skip(PoiCategory.RESTAURANT, 2L)
        store.skip(PoiCategory.CAFE, 9L)

        assertEquals(setOf(1L, 2L), store.skipped.value[PoiCategory.RESTAURANT])
        assertEquals(setOf(9L), store.skipped.value[PoiCategory.CAFE])
    }

    @Test
    fun `skipping a wrapped pick starts a fresh cycle with only that id`() {
        // Every candidate skipped → tile wrapped back to the nearest (id 1). Skipping it again must
        // show the second-nearest, so only id 1 stays skipped.
        val store = SkipStore()
        store.skip(PoiCategory.RESTAURANT, 1L)
        store.skip(PoiCategory.RESTAURANT, 2L)
        store.skip(PoiCategory.RESTAURANT, 3L)

        store.skip(PoiCategory.RESTAURANT, 1L, wrapped = true)

        assertEquals(setOf(1L), store.skipped.value[PoiCategory.RESTAURANT])
    }

    @Test
    fun `a stale repeat tap for an already-skipped id is ignored`() {
        // Hardware 2026-10-04: second chevron tap 1.6 s after the first still carried the old osm id
        // because the tile had not re-rendered yet. It must not restart the cycle.
        val store = SkipStore()
        store.skip(PoiCategory.RESTAURANT, 1L)
        store.skip(PoiCategory.RESTAURANT, 2L)

        store.skip(PoiCategory.RESTAURANT, 2L)

        assertEquals(setOf(1L, 2L), store.skipped.value[PoiCategory.RESTAURANT])
    }

    @Test
    fun `clear drops every category`() {
        val store = SkipStore()
        store.skip(PoiCategory.RESTAURANT, 1L)
        store.skip(PoiCategory.CAFE, 2L)
        store.clear()
        assertTrue(store.skipped.value.isEmpty())
    }

    @Test
    fun `route change clears skips, same route id does not`() = runTest(UnconfinedTestDispatcher()) {
        val routeIds = MutableSharedFlow<String?>()
        val store = SkipStore()
        store.resetOnRouteChange(routeIds, backgroundScope)

        routeIds.emit("route-a")
        store.skip(PoiCategory.RESTAURANT, 1L)
        routeIds.emit("route-a")
        assertEquals(setOf(1L), store.skipped.value[PoiCategory.RESTAURANT], "re-emitting the same route must keep skips")

        routeIds.emit("route-b")
        assertTrue(store.skipped.value.isEmpty(), "a new route must clear skips")

        store.skip(PoiCategory.RESTAURANT, 2L)
        routeIds.emit(null)
        assertTrue(store.skipped.value.isEmpty(), "ending navigation must clear skips")
    }
}
