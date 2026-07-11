package de.eckstein.gpsview.coordinates

/** WGS84 geographic coordinate. Zero Android imports (constitution I). */
data class LatLon(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}

/** Satellite used/visible ratio reported by [de.eckstein.gpsview.data.GnssSource]. */
data class SatelliteCount(val used: Int, val visible: Int) {
    init {
        require(visible >= 0) { "visible must be >= 0: $visible" }
        require(used in 0..visible) { "used ($used) must be within 0..visible ($visible)" }
    }
}

/**
 * One instant of raw sensor truth. Only [latLon] and [timestamp] are guaranteed; every other
 * field is nullable and populated only when the platform reported it. Never carries formatted
 * strings or derived coordinates — those are pure functions of this snapshot, computed at UI-state
 * build time (constitution I, III; data-model.md).
 */
data class PositionSnapshot(
    val latLon: LatLon,
    val horizontalAccuracyM: Float?,
    val ellipsoidalAltitudeM: Double?,
    val verticalAccuracyM: Float?,
    val mslAltitudeM: Double?,
    val mslAccuracyM: Float?,
    val timestamp: Long,
    val satellites: SatelliteCount?,
)
