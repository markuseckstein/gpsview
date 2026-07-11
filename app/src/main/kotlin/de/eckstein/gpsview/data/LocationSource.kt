package de.eckstein.gpsview.data

import de.eckstein.gpsview.coordinates.PositionSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The enforced Android boundary (constitution I) for position fixes. Implementations are cold
 * [Flow]s: no location registration exists before collection starts, and collection cancellation
 * tears the registration down immediately (constitution II; contracts/location-sources.md).
 */
interface LocationSource {
    /**
     * Cold flow of MSL-enriched position fixes. Always emitted with `satellites = null` — the
     * ViewModel merges in [GnssSource]'s latest [de.eckstein.gpsview.coordinates.SatelliteCount].
     */
    fun positions(): Flow<PositionSnapshot>
}
