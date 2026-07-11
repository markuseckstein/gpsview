package de.eckstein.gpsview.coordinates

import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden fixtures per contracts/coordinates-api.md §Heights and SPEC.md §6.4. */
class HeightFormatTest {

    @Test
    fun `whole-meter display rounds to the nearest meter`() {
        assertEquals("487 m", formatHeightDisplay(487.0))
        assertEquals("487 m", formatHeightDisplay(486.6))
        assertEquals("453 m", formatHeightDisplay(452.5))
    }

    @Test
    fun `null height renders as a dash, never zero`() {
        assertEquals("—", formatHeightDisplay(null))
    }

    @Test
    fun `copy payload is the bare whole-meter value`() {
        assertEquals("487 m", heightCopyPayload(487.0))
        assertEquals("453 m", heightCopyPayload(452.6))
    }
}
