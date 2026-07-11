package de.eckstein.gpsview.coordinates

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden fixture per SPEC.md §6.6 / contracts/coordinates-api.md §Share block, fixed timestamp injected. */
class ShareFormattingTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val fixedTimestamp =
        ZonedDateTime.of(2026, 7, 11, 14, 32, 0, 0, zone).toInstant().toEpochMilli()

    private val munichFull =
        PositionSnapshot(
            latLon = LatLon(48.137154, 11.575382),
            horizontalAccuracyM = 4f,
            ellipsoidalAltitudeM = 487.0,
            verticalAccuracyM = 2f,
            mslAltitudeM = 453.0,
            mslAccuracyM = 3f,
            timestamp = fixedTimestamp,
            satellites = SatelliteCount(used = 18, visible = 24),
        )

    @Test
    fun `full share block matches the SPEC worked example structure`() {
        val block = shareBlock(munichFull, UtmrefPrecision.M100, zone)

        val expected =
            """
            Standort (GPSView)
            UTMREF: 32U PU 915 347
            Koordinaten: 48.137154, 11.575382
            Plus Code: 8FWH4HPG+V5
            Höhe: 487 m (ellipsoidisch) · 453 m (NHN)
            Genauigkeit: ±4 m · 11.07.2026 14:32
            https://www.google.com/maps?q=48.137154,11.575382
            """
                .trimIndent()

        assertEquals(expected, block)
    }

    @Test
    fun `missing height and accuracy render as dashes without dropping lines`() {
        val partial =
            munichFull.copy(
                horizontalAccuracyM = null,
                ellipsoidalAltitudeM = null,
                mslAltitudeM = null,
            )
        val block = shareBlock(partial, UtmrefPrecision.M100, zone)

        assertEquals(
            true,
            block.contains("Höhe: — (ellipsoidisch) · — (NHN)"),
        )
        assertEquals(true, block.contains("Genauigkeit: — · 11.07.2026 14:32"))
    }
}
