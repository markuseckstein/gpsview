package de.eckstein.gpsview.data

import de.eckstein.gpsview.coordinates.SatelliteCount
import kotlinx.coroutines.flow.Flow

/**
 * The enforced Android boundary (constitution I) for satellite metadata. Cold flow; may never
 * emit (e.g. indoors, no GnssStatus yet) without affecting position emissions
 * (contracts/location-sources.md).
 */
interface GnssSource {
    /** Cold flow of satellite used/visible summaries. */
    fun satellites(): Flow<SatelliteCount>
}
