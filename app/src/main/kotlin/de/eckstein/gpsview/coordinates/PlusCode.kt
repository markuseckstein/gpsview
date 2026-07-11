package de.eckstein.gpsview.coordinates

import com.google.openlocationcode.OpenLocationCode

/** Full global Plus Code (SPEC.md §6.3) — never the locality short form. */
fun plusCode(latLon: LatLon): String = OpenLocationCode(latLon.latitude, latLon.longitude).code
