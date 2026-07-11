---
name: Research GNSS metadata and height APIs
labels: [wayfinder:research]
status: closed
assignee: claude
blocked-by: []
---

## Question

How does GPSView obtain, on Android 14+, the GNSS metadata it must display: horizontal/vertical accuracy, satellites visible vs used-in-fix, and height as both ellipsoidal and MSL values?

To resolve (AFK research, `/research`):

- `GnssStatus` callback: what satellite detail is available (visible, used in fix, per-constellation, C/N0), and its relationship to `LocationManager` registration.
- Height: confirm `Location.getAltitude()` semantics (WGS84 ellipsoid), `Location.hasMslAltitude()` / `getMslAltitudeMeters()` availability on Android 14, and Play services `AltitudeConverter` (accuracy of its geoid model over Bavaria, offline behavior, API constraints).
- Vertical accuracy fields for both height flavors.
- Any interactions/limitations when the position itself comes from the fused provider (does fused output carry MSL altitude / satellite association?).

Deliverable: markdown summary linked here, stating exactly which APIs the spec should mandate for each displayed metadatum.

## Resolution

Findings: [wayfinder/research/02-gnss-metadata-and-height.md](../research/02-gnss-metadata-and-height.md).

The spec should mandate:

- **Horizontal accuracy:** `Location.getAccuracy()` guarded by `hasAccuracy()` (API 1, 68th-percentile).
- **Vertical accuracy (ellipsoidal):** `Location.getVerticalAccuracyMeters()` guarded by `hasVerticalAccuracy()` (API 26).
- **Vertical accuracy (MSL):** `Location.getMslAltitudeAccuracyMeters()` guarded by `hasMslAltitudeAccuracy()` (API 34).
- **Satellites visible:** `GnssStatus.getSatelliteCount()`.
- **Satellites used-in-fix:** iterate `usedInFix(i)` across `0 until getSatelliteCount()` — no direct count method. Never use the deprecated `Location.getExtras()["satellites"]`.
- **Height, ellipsoidal:** `Location.getAltitude()` guarded by `hasAltitude()` (API 1, always WGS84).
- **Height, MSL:** `Location.getMslAltitudeMeters()` guarded by `hasMslAltitude()` (API 34); if unset on the fix, convert via a reused `android.location.altitude.AltitudeConverter` instance (`addMslAltitudeToLocation`, off main thread).

Satellite data requires a **separate** subsystem from position: `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` alongside whatever provides the position fix — there is no single API returning both.

**Correction to this ticket's premise:** `AltitudeConverter` is not a Play services/GMS class (`com.google.android.gms.location.altitude.AltitudeConverter` does not exist — confirmed 404). It's the platform class `android.location.altitude.AltitudeConverter` (API 34+), backed by locally bundled data with no documented network dependency — so MSL conversion needs no Play-services dependency and works offline.

**Flagged for on-device verification, not resolvable from docs alone:** whether `FusedLocationProviderClient` fixes arrive with `hasMslAltitude()` already true or always need the `AltitudeConverter` fallback; whether `GnssStatus.Callback` registration alone (without a concurrent `requestLocationUpdates`) reliably yields callbacks; the geoid model's accuracy/coverage over Bavaria specifically.
