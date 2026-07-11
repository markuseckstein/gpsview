package de.eckstein.gpsview

import de.eckstein.gpsview.coordinates.PositionSnapshot
import de.eckstein.gpsview.coordinates.SatelliteCount
import de.eckstein.gpsview.data.GnssSource
import de.eckstein.gpsview.data.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Test double for [LocationSource]; lets tests script emission order (contracts/location-sources.md). */
class FakeLocationSource : LocationSource {
    private val flow = MutableSharedFlow<PositionSnapshot>(extraBufferCapacity = 8)

    override fun positions(): Flow<PositionSnapshot> = flow

    suspend fun emit(snapshot: PositionSnapshot) = flow.emit(snapshot)
}

/** Test double for [GnssSource]; lets tests script emission order (contracts/location-sources.md). */
class FakeGnssSource : GnssSource {
    private val flow = MutableSharedFlow<SatelliteCount>(extraBufferCapacity = 8)

    override fun satellites(): Flow<SatelliteCount> = flow

    suspend fun emit(count: SatelliteCount) = flow.emit(count)
}
