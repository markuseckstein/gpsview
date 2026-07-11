---
name: Research coordinate conversion libraries
labels: [wayfinder:research]
status: closed
assignee: claude
blocked-by: []
---

## Question

Which offline libraries should convert WGS84 lat/lon into UTMREF/MGRS and Plus Codes, and is offline what3words truly infeasible?

To resolve (AFK research, `/research`):

- **UTMREF/MGRS:** candidate JVM/Kotlin libraries (e.g. mgrs-java/mgrs-android, GeographicLib bindings, Proj-based) — correctness for zone 32U/33U (Bavaria), license, size, maintenance. Confirm the exact grid-reference formatting Bavarian BOS use (spacing, precision).
- **Plus Codes:** the reference `open-location-code` Java library — fitness and license.
- **what3words:** confirm (or refute) that offline conversion requires their paid proprietary SDK with no free personal tier. If a legitimate offline path exists for a personal app, note its terms; otherwise the standing decision applies: drop w3w, ship Plus Codes.

Deliverable: markdown summary linked here, naming the libraries the spec should mandate.

## Resolution

Findings: [wayfinder/research/04-coordinate-conversion-libraries.md](../research/04-coordinate-conversion-libraries.md).

The spec should mandate:

- **UTMREF/MGRS:** `mil.nga:mgrs:2.1.3` (plain JVM artifact) + its dependency `mil.nga:grid:1.1.2`. MIT-licensed, NGA-authored (spec-authoritative), ~80 KB combined, zero third-party runtime deps. **Not** `mil.nga.mgrs:mgrs-android` — it only adds Google-Maps tile rendering and drags in `com.google.android.gms:play-services-maps`, dead weight for a MapLibre-based app. GeographicLib-Java has no MGRS/UTMUPS support in its actual published Java artifact (verified by inspecting the sources JAR, contra a wrong secondary-source summary); Proj4J has no MGRS support either and is ~5x heavier.
- **Bavarian BOS UTMREF format**, confirmed from an official Staatliche Feuerwehrschulen Bayerns / Bayerisches Innenministerium training document: UTMREF and MGRS are explicitly synonymous. Display format is `<Zonenfeld> <100km-Quadrat> <Ostwert> <Nordwert>` (e.g. `32U NA 648 215`), always an even digit count split evenly between easting/northing (4/6/8/10 digits → 1 km/100 m/10 m/1 m). GPSView should default to full 10-digit (1 m) precision, since it has a live GPS fix rather than a hand-plotted map reading (where 6-digit/100 m is the taught default).
- **Correction to this ticket's premise:** Bavaria spans **four** MGRS grid zones (32T, 32U, 33T, 33U), not just 32U/33U — the state straddles both the zone-32/33 and band-T/U lines near Regensburg/Cham. Any zone-boundary correctness test suite must cover all four.
- **Plus Codes:** `com.google.openlocationcode:openlocationcode:1.0.4`. Apache-2.0, Google's own reference implementation, 8.6 KB, zero runtime dependencies, offline by construction.
- **what3words:** standing decision (drop w3w, ship Plus Codes) confirmed correct against what3words' own pricing page and API licence agreement. The Free tier excludes all conversion; the only offline path (Mobile SDK/API Server) is an Enterprise product sold via annual/per-device licence with no self-serve or personal-developer path; the licence agreement additionally forbids caching results as a substitute for live API calls.

**Flagged for on-device/unit-test verification, not resolvable from docs alone:** whether `MGRS.toString()`/`coordinate(GridType)` emit the Bavarian-BOS spaced grouping or NGA's compact form (GPSView likely needs its own formatting layer regardless); live test vectors confirming correct zone resolution at Bavaria's 12°E/48°N four-zone crossing (one fixture per zone-field quadrant, plus the document's own worked example `32U NA 648 215` for the EU midpoint near Gadheim); a build/dependency-resolution smoke test of `mil.nga:mgrs` against a current Android Gradle toolchain (no release since April 2024).
