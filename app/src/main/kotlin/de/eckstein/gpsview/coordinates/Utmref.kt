package de.eckstein.gpsview.coordinates

import mil.nga.mgrs.MGRS
import mil.nga.mgrs.grid.GridType

/** Even digit precisions only — no odd forms exist (SPEC.md §6.1). */
enum class UtmrefPrecision(val digits: Int, internal val gridType: GridType) {
    KM(4, GridType.KILOMETER),
    M100(6, GridType.HUNDRED_METER),
    M10(8, GridType.TEN_METER),
    M1(10, GridType.METER),
}

/**
 * Bavarian BOS-format UTMREF/MGRS grid reference: `Zonenfeld 100kmQuadrat Ostwert Nordwert`,
 * single spaces. Wraps the plain-JVM `mil.nga:mgrs` library, which itself emits compact strings
 * with no delimiters (research.md E1) — this formatter owns the spacing.
 */
fun utmref(latLon: LatLon, precision: UtmrefPrecision = UtmrefPrecision.M1): String {
    val mgrs = MGRS.from(latLon.longitude, latLon.latitude)
    val zoneAndBand = mgrs.coordinate(GridType.GZD)
    val gridSquare = mgrs.coordinate(GridType.HUNDRED_KILOMETER).removePrefix(zoneAndBand)

    val eastingNorthing = mgrs.getEastingAndNorthing(precision.gridType)
    val half = eastingNorthing.length / 2
    val easting = eastingNorthing.substring(0, half)
    val northing = eastingNorthing.substring(half)

    return "$zoneAndBand $gridSquare $easting $northing"
}
