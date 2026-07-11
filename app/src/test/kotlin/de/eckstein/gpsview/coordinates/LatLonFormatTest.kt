package de.eckstein.gpsview.coordinates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Golden fixtures per contracts/coordinates-api.md §Lat/lon display vs copy. */
class LatLonFormatTest {

    private val munich = LatLon(48.137154, 11.575382)

    @Test
    fun `decimal display uses German comma and degree glyph`() {
        val display = formatLatLonDisplay(munich, LatLonMode.DECIMAL)
        assertTrue(display.contains("48,137154°"))
        assertTrue(display.contains("11,575382°"))
    }

    @Test
    fun `decimal copy payload uses dots, lat comma lon order, no degree glyph`() {
        assertEquals("48.137154, 11.575382", latLonCopyPayload(munich, LatLonMode.DECIMAL))
    }

    @Test
    fun `DMS display is binding format with German comma seconds`() {
        val display = formatLatLonDisplay(munich, LatLonMode.DMS)
        assertTrue(display.contains("48° 8′ 13,75″ N"))
        assertTrue(display.contains("11° 34′ 31,38″ O"))
    }

    @Test
    fun `DMS copy payload is identical to DMS display`() {
        val display = formatLatLonDisplay(munich, LatLonMode.DMS)
        val copy = latLonCopyPayload(munich, LatLonMode.DMS)
        assertEquals(display, copy)
    }

    @Test
    fun `southern and western hemispheres use S and W letters`() {
        val southWest = LatLon(-33.865143, -63.987209)
        val display = formatLatLonDisplay(southWest, LatLonMode.DMS)
        assertTrue(display.contains(" S"))
        assertTrue(display.contains(" W"))
    }

    @Test
    fun `DMS round-trips to decimal within 6-decimal-place tolerance`() {
        val display = formatLatLonDisplay(munich, LatLonMode.DMS)
        val (latDecimal, lonDecimal) = parseDmsPair(display)

        assertTrue(abs(latDecimal - munich.latitude) < 0.000001 * 10) // seconds are rounded to 2dp
        assertTrue(abs(lonDecimal - munich.longitude) < 0.000001 * 10)
    }

    /** Test-only inverse of the binding `D° M′ S,SS″ H` format — not part of the public API. */
    private fun parseDmsPair(display: String): Pair<Double, Double> {
        val pattern = Regex("""(\d+)° (\d+)′ (\d+,\d+)″ ([NSOW])""")
        val matches = pattern.findAll(display).toList()
        check(matches.size == 2) { "expected two DMS components in: $display" }

        fun toDecimal(match: MatchResult): Double {
            val (deg, min, sec, hemi) = match.destructured
            val value = deg.toInt() + min.toInt() / 60.0 + sec.replace(',', '.').toDouble() / 3600.0
            return if (hemi == "S" || hemi == "W") -value else value
        }

        return toDecimal(matches[0]) to toDecimal(matches[1])
    }
}
