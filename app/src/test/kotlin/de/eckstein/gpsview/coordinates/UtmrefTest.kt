package de.eckstein.gpsview.coordinates

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden fixtures per contracts/coordinates-api.md, frozen against the pinned `mil.nga:mgrs`
 * 2.1.3 library (research.md E1/T018 — the library emits compact strings, GPSView's own BOS
 * formatter adds the Merkblatt 9.008 spacing).
 */
class UtmrefTest {

    @Test
    fun `Gadheim known-good reference at 6-digit precision`() {
        val gadheim = LatLon(49.8431, 9.9019)
        assertEquals("32U NA 648 215", utmref(gadheim, UtmrefPrecision.M100))
    }

    @Test
    fun `Gadheim at default 10-digit precision`() {
        val gadheim = LatLon(49.8431, 9.9019)
        assertEquals("32U NA 64846 21576", utmref(gadheim))
        assertEquals("32U NA 64846 21576", utmref(gadheim, UtmrefPrecision.M1))
    }

    @Test
    fun `zone 32T fixture near Garmisch`() {
        val garmisch = LatLon(47.49, 11.10)
        assertEquals("32T PT 58185 61755", utmref(garmisch, UtmrefPrecision.M1))
    }

    @Test
    fun `zone 32U fixture near Wurzburg-Gadheim`() {
        val gadheim = LatLon(49.8431, 9.9019)
        assertEquals("32U NA 64846 21576", utmref(gadheim, UtmrefPrecision.M1))
    }

    @Test
    fun `zone 33U fixture near Cham`() {
        val cham = LatLon(49.22, 12.66)
        assertEquals("33U UQ 29608 54548", utmref(cham, UtmrefPrecision.M1))
    }

    @Test
    fun `zone 33T fixture near Berchtesgaden`() {
        val berchtesgaden = LatLon(47.63, 13.00)
        assertEquals("33T UN 49748 77115", utmref(berchtesgaden, UtmrefPrecision.M1))
    }

    @Test
    fun `all four precisions produce even-split digit groups from the same point`() {
        val munich = LatLon(48.137154, 11.575382)

        val km = utmref(munich, UtmrefPrecision.KM)
        val m100 = utmref(munich, UtmrefPrecision.M100)
        val m10 = utmref(munich, UtmrefPrecision.M10)
        val m1 = utmref(munich, UtmrefPrecision.M1)

        fun digitGroups(s: String): Pair<Int, Int> {
            val parts = s.substringAfter(' ').substringAfter(' ').split(' ')
            return parts[0].length to parts[1].length
        }

        assertEquals(2 to 2, digitGroups(km))
        assertEquals(3 to 3, digitGroups(m100))
        assertEquals(4 to 4, digitGroups(m10))
        assertEquals(5 to 5, digitGroups(m1))

        // lower precision truncates the higher-precision string's grid square, no rounding
        assertEquals("32U PU 91 34", km)
        assertEquals("32U PU 915 347", m100)
        assertEquals("32U PU 9159 3475", m10)
        assertEquals("32U PU 91595 34752", m1)
    }
}
