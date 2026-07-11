package de.eckstein.gpsview.coordinates

import kotlin.math.roundToLong

/** Missing-value placeholder for dashed rows (constitution III — never stale, never zero). */
private const val DASH = "—"

/** Display string for a height row (SPEC.md §6.4): whole meters, or a dash when unavailable. */
fun formatHeightDisplay(meters: Double?): String =
    if (meters == null) DASH else "${meters.roundToLong()} m"

/** Copy payload for a height row: bare whole-meter value, e.g. `487 m`. */
fun heightCopyPayload(meters: Double): String = "${meters.roundToLong()} m"
