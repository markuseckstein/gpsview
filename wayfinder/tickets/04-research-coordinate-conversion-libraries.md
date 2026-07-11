---
name: Research coordinate conversion libraries
labels: [wayfinder:research]
status: open
assignee:
blocked-by: []
---

## Question

Which offline libraries should convert WGS84 lat/lon into UTMREF/MGRS and Plus Codes, and is offline what3words truly infeasible?

To resolve (AFK research, `/research`):

- **UTMREF/MGRS:** candidate JVM/Kotlin libraries (e.g. mgrs-java/mgrs-android, GeographicLib bindings, Proj-based) — correctness for zone 32U/33U (Bavaria), license, size, maintenance. Confirm the exact grid-reference formatting Bavarian BOS use (spacing, precision).
- **Plus Codes:** the reference `open-location-code` Java library — fitness and license.
- **what3words:** confirm (or refute) that offline conversion requires their paid proprietary SDK with no free personal tier. If a legitimate offline path exists for a personal app, note its terms; otherwise the standing decision applies: drop w3w, ship Plus Codes.

Deliverable: markdown summary linked here, naming the libraries the spec should mandate.
