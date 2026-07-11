package de.eckstein.gpsview.coordinates

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class LatLonMode { DECIMAL, DMS }

/**
 * Display string for both components (SPEC.md §6.2). Decimal: German comma, 6 places, `°`.
 * DMS: `D° M′ S,SS″ H` — unpadded degrees/minutes, 2-decimal German-comma seconds, prime
 * U+2032/double prime U+2033, hemisphere letters N/S and O/W. Layout of the two components into
 * rows is a UI concern; this returns them joined by " / ".
 */
fun formatLatLonDisplay(latLon: LatLon, mode: LatLonMode): String =
    when (mode) {
        LatLonMode.DECIMAL ->
            "${decimalString(latLon.latitude)}° / ${decimalString(latLon.longitude)}°"
        LatLonMode.DMS ->
            "${dmsString(latLon.latitude, isLatitude = true)} / ${dmsString(latLon.longitude, isLatitude = false)}"
    }

/**
 * Copy payload (SPEC.md §6.5). Decimal deliberately deviates from display for pasteability: dots,
 * `lat, lon` order, no degree glyph. DMS copy is identical to DMS display.
 */
fun latLonCopyPayload(latLon: LatLon, mode: LatLonMode): String =
    when (mode) {
        LatLonMode.DECIMAL ->
            "%.6f, %.6f".format(Locale.ROOT, latLon.latitude, latLon.longitude)
        LatLonMode.DMS -> formatLatLonDisplay(latLon, LatLonMode.DMS)
    }

private fun decimalString(value: Double): String = "%.6f".format(Locale.GERMANY, value)

private fun dmsString(decimalDegrees: Double, isLatitude: Boolean): String {
    val hemisphere =
        if (isLatitude) {
            if (decimalDegrees >= 0) "N" else "S"
        } else {
            if (decimalDegrees >= 0) "O" else "W"
        }

    // Round once at hundredths-of-a-second resolution so degree/minute/second carry correctly
    // (e.g. 59,995″ rounding up must roll into the next minute), then derive all three by
    // integer division of that single rounded value.
    val totalHundredthsOfSecond = (abs(decimalDegrees) * 3600.0 * 100.0).roundToLong()
    val degrees = totalHundredthsOfSecond / 360_000
    val remainderAfterDegrees = totalHundredthsOfSecond % 360_000
    val minutes = remainderAfterDegrees / 6_000
    val secondHundredths = remainderAfterDegrees % 6_000
    val seconds = secondHundredths / 100
    val secondFraction = secondHundredths % 100

    val secondsString = "$seconds,${secondFraction.toString().padStart(2, '0')}"
    return "$degrees° $minutes′ $secondsString″ $hemisphere"
}
