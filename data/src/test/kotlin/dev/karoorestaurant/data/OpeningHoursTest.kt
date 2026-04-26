package dev.karoorestaurant.data

import dev.karoorestaurant.data.poi.OpeningHours
import dev.karoorestaurant.data.poi.OpeningHours.Status
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningHoursTest {

    private val mondayNoon = LocalDateTime.of(2026, 4, 27, 12, 0)
    private val mondayMidnight = LocalDateTime.of(2026, 4, 27, 0, 30)
    private val saturdayMorning = LocalDateTime.of(2026, 5, 2, 9, 30)
    private val saturdayEvening = LocalDateTime.of(2026, 5, 2, 18, 0)

    @Test
    fun `null tag is unknown`() {
        assertTrue(OpeningHours.evaluate(null) is Status.Unknown)
    }

    @Test
    fun `24-7 is always open`() {
        assertEquals(Status.Open, OpeningHours.evaluate("24/7", mondayMidnight))
        assertEquals(Status.Open, OpeningHours.evaluate("24/7", saturdayEvening))
    }

    @Test
    fun `weekday range and time span`() {
        val tag = "Mo-Fr 08:00-18:00"
        assertEquals(Status.Open, OpeningHours.evaluate(tag, mondayNoon))
        assertEquals(Status.Closed, OpeningHours.evaluate(tag, mondayMidnight))
        assertEquals(Status.Closed, OpeningHours.evaluate(tag, saturdayMorning))
    }

    @Test
    fun `multi-rule with saturday window`() {
        val tag = "Mo-Fr 08:00-18:00; Sa 09:00-13:00"
        assertEquals(Status.Open, OpeningHours.evaluate(tag, saturdayMorning))
        assertEquals(Status.Closed, OpeningHours.evaluate(tag, saturdayEvening))
    }

    @Test
    fun `off modifier closes a window`() {
        val tag = "Mo-Su 08:00-22:00; Mo off"
        assertEquals(Status.Closed, OpeningHours.evaluate(tag, mondayNoon))
        assertEquals(Status.Open, OpeningHours.evaluate(tag, saturdayMorning))
    }
}
