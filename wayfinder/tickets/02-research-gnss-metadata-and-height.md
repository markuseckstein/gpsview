---
name: Research GNSS metadata and height APIs
labels: [wayfinder:research]
status: open
assignee:
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
