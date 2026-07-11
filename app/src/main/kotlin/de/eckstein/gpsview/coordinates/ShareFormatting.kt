package de.eckstein.gpsview.coordinates

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

private val shareDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

/**
 * Builds the German position-share block (SPEC.md §6.6): UTMREF first, whole snapshot, dotted
 * decimals, tappable `https` Google Maps link. Pure — takes the fix's own timestamp (never "now")
 * so a stale share stays honest, and an injected [zoneId] for deterministic formatting in tests.
 */
fun shareBlock(
    snapshot: PositionSnapshot,
    utmrefPrecision: UtmrefPrecision = UtmrefPrecision.M1,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val latLon = snapshot.latLon
    val timestampText =
        Instant.ofEpochMilli(snapshot.timestamp).atZone(zoneId).format(shareDateFormatter)
    val accuracyText =
        snapshot.horizontalAccuracyM?.let { "±${round(it).toInt()} m" } ?: "—"
    val mapsLink = "%.6f,%.6f".format(Locale.ROOT, latLon.latitude, latLon.longitude)

    return """
        Standort (GPSView)
        UTMREF: ${utmref(latLon, utmrefPrecision)}
        Koordinaten: ${latLonCopyPayload(latLon, LatLonMode.DECIMAL)}
        Plus Code: ${plusCode(latLon)}
        Höhe: ${formatHeightDisplay(snapshot.ellipsoidalAltitudeM)} (ellipsoidisch) · ${formatHeightDisplay(snapshot.mslAltitudeM)} (NHN)
        Genauigkeit: $accuracyText · $timestampText
        https://www.google.com/maps?q=$mapsLink
    """
        .trimIndent()
}
