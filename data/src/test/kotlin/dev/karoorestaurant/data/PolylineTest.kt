package dev.karoorestaurant.data

import dev.karoorestaurant.data.route.Polyline
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineTest {

    @Test
    fun `decodes Google example`() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val expected = listOf(
            38.5 to -120.2,
            40.7 to -120.95,
            43.252 to -126.453,
        )
        val actual = Polyline.decode(encoded)
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.lat, 1e-5)
            assertEquals(e.second, a.lon, 1e-5)
        }
    }

    @Test
    fun `decodes empty string to empty list`() {
        assertTrue(Polyline.decode("").isEmpty())
    }
}
