package de.eckstein.gpsview.coordinates

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixture verified against the pinned `com.google.openlocationcode` 1.0.4 reference library
 * (research.md fixture-correction note, T017/T020) — the contract's originally stated
 * `8FWH4HX8+9C` did not match a live run of that library and was corrected to `8FWH4HPG+V5`.
 */
class PlusCodeTest {

    @Test
    fun `Munich fixture produces the full global code`() {
        val munich = LatLon(48.137154, 11.575382)
        assertEquals("8FWH4HPG+V5", plusCode(munich))
    }
}
