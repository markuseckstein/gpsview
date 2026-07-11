package de.eckstein.gpsview.data

import android.content.Context
import android.location.Location
import android.location.altitude.AltitudeConverter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import de.eckstein.gpsview.coordinates.LatLon
import de.eckstein.gpsview.coordinates.PositionSnapshot
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * FLP-backed [LocationSource]. Cold, foreground-only (constitution II): registration happens on
 * collect, [FusedLocationProviderClient.removeLocationUpdates] runs in `awaitClose`. Every
 * emission is MSL-enriched before it leaves this class (contracts/location-sources.md §3) via a
 * single app-lifetime [AltitudeConverter] reused across fixes, converted off the caller's thread.
 */
class FusedLocationSource(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
) : LocationSource {

    private val altitudeConverter = AltitudeConverter()
    private val directExecutor = Executor { it.run() }

    override fun positions(): Flow<PositionSnapshot> =
        callbackFlow {
                val request =
                    LocationRequest.Builder(/* intervalMillis = */ 1000L)
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateDelayMillis(0)
                        .setWaitForAccurateLocation(true)
                        .build()

                val listener = LocationListener { location -> trySend(location) }

                try {
                    fusedLocationClient.requestLocationUpdates(request, directExecutor, listener)
                } catch (e: SecurityException) {
                    close(e)
                }

                awaitClose { fusedLocationClient.removeLocationUpdates(listener) }
            }
            .map { location -> enrich(location) }

    private suspend fun enrich(location: Location): PositionSnapshot {
        if (!location.hasMslAltitude()) {
            try {
                withContext(Dispatchers.IO) {
                    altitudeConverter.addMslAltitudeToLocation(context, location)
                }
            } catch (e: IOException) {
                // MSL fields stay null.
            } catch (e: IllegalArgumentException) {
                // MSL fields stay null.
            }
        }
        return location.toPositionSnapshot()
    }

    private fun Location.toPositionSnapshot(): PositionSnapshot =
        PositionSnapshot(
            latLon = LatLon(latitude, longitude),
            horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
            ellipsoidalAltitudeM = if (hasAltitude()) altitude else null,
            verticalAccuracyM = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
            mslAltitudeM = if (hasMslAltitude()) mslAltitudeMeters else null,
            mslAccuracyM = if (hasMslAltitudeAccuracy()) mslAltitudeAccuracyMeters else null,
            timestamp = time,
            satellites = null,
        )
}
