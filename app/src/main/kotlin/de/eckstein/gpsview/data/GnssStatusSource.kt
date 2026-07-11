package de.eckstein.gpsview.data

import android.location.GnssStatus
import android.location.LocationManager
import de.eckstein.gpsview.coordinates.SatelliteCount
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GnssStatus-backed [GnssSource]. Cold, foreground-only (constitution II): registration happens
 * on collect, unregisters in `awaitClose`. Never reads the deprecated location extras
 * (contracts/location-sources.md §GnssSource).
 *
 * [LocationManager.registerGnssStatusCallback] requires `ACCESS_FINE_LOCATION` specifically —
 * unlike FLP fixes, a coarse-only grant is not enough and throws [SecurityException]. Per the
 * contract ("may never emit; must never error the UI"), that failure is swallowed here: the flow
 * simply never emits rather than propagating the exception through `combine()` and crashing the
 * whole position pipeline (observed on-device with coarse-only permission, US5/T046).
 */
class GnssStatusSource(private val locationManager: LocationManager) : GnssSource {

    private val directExecutor = Executor { it.run() }

    override fun satellites(): Flow<SatelliteCount> = callbackFlow {
        val callback =
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val visible = status.satelliteCount
                    val used = (0 until visible).count { status.usedInFix(it) }
                    trySend(SatelliteCount(used = used, visible = visible))
                }
            }

        try {
            locationManager.registerGnssStatusCallback(directExecutor, callback)
        } catch (e: SecurityException) {
            awaitClose {}
            return@callbackFlow
        }

        awaitClose { locationManager.unregisterGnssStatusCallback(callback) }
    }
}
