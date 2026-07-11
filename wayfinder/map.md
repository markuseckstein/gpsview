---
name: gpsview-map
labels: [wayfinder:map]
status: open
---

# GPSView — Wayfinder Map

## Destination

An **implementation-ready spec** for a personal Android app ("GPSView") that shows live details of the current location: coordinates in multiple systems (lat/lon, UTMREF/MGRS as used by Bavarian fire services, Plus Codes), GNSS metadata (accuracy, satellite counts, ellipsoidal **and** sea-level height), and the position on an open-source map with satellite imagery — battery-frugal and strictly foreground-only. The map also shows an optional overlay of Bavarian cadastral parcel boundaries (Flurstücke), using only the free/open data available. The map is done when every design decision is resolved and the spec could be handed to a single build effort.

## Notes

Standing constraints, settled in the charting session (2026-07-09):

- **Stack:** Kotlin + Jetpack Compose, min SDK 34 (Android 14+), Google Play services allowed.
- **Distribution:** personal sideload only — loosest licensing constraints.
- **UI language:** German.
- **Coordinates:** lat/lon + UTMREF/MGRS; what3words only if offline conversion is genuinely possible, otherwise replaced by Plus Codes (Open Location Code).
- **Height:** show both ellipsoidal (raw GPS) and MSL/sea-level height, clearly labeled.
- **Behavior:** live updates while the app is visible, stopping instantly when backgrounded; tap-to-copy and share per coordinate format.
- Skills to use per ticket type: `/research` for research tickets, `/prototype` for prototype tickets, `/grilling` + `/domain-modeling` for grilling tickets.
- **Cadastral parcels (Flurstücke):** show parcel boundary lines as an optional overlay, using only Bavaria's free ALKIS-Parzellarkarte (licensed CC BY 4.0 per the WMS/OpenData metadata, but the LDBV product page singles it out as CC BY-ND 4.0 — unconfirmed, see [Research Bavarian cadastral parcel overlay integration](tickets/08-research-cadastral-parcel-overlay.md)). No Flurstücksnummer/Flurnummer labels — those require a paid LDBV/GeodatenOnline license, which conflicts with the sideload/loosest-licensing stance, so labeling is out of scope for now. Decided 2026-07-11.

**Tracker conventions (local markdown):** tickets are files in `wayfinder/tickets/`; frontmatter carries `status` (open/closed), `assignee` (the claim — empty means unclaimed), `labels`, and `blocked-by` (list of ticket file names). A ticket is on the frontier when open, unassigned, and everything in its `blocked-by` is closed. Resolutions are appended to the ticket file under `## Resolution`.

## Decisions so far

<!-- one line per closed ticket: gist + link -->

- [Research map library and satellite imagery sources](tickets/01-research-map-library-and-imagery.md) — MapLibre Native + `maplibre-compose` for the map library; OpenFreeMap (vector, uncapped, no key) for the base map layer; Bavarian DOP WMS (CC BY 4.0, unauthenticated) for the satellite layer. Findings: [wayfinder/research/01-map-library-and-imagery.md](research/01-map-library-and-imagery.md).
- [Research Bavarian cadastral parcel overlay integration](tickets/08-research-cadastral-parcel-overlay.md) — free ALKIS-Parzellarkarte WMS as a third MapLibre raster layer (outline-only style, transparent PNG, toggled independently, Bavaria-wide single endpoint); license text conflicts between sources (CC BY 4.0 vs CC BY-ND 4.0), flagged as a follow-up task. Findings: [wayfinder/research/08-cadastral-parcel-overlay.md](research/08-cadastral-parcel-overlay.md).
- 09-task-confirm-parzellarkarte-license - CC BY 4.0
- [Research GNSS metadata and height APIs](tickets/02-research-gnss-metadata-and-height.md) — `Location`/`GnssStatus` platform APIs for all metadata (horizontal/vertical/MSL accuracy, satellites visible vs used-in-fix, ellipsoidal + MSL height); MSL support (`hasMslAltitude`, `AltitudeConverter`) is API 34+ platform, *not* Play services as assumed — corrects the ticket's premise. Findings: [wayfinder/research/02-gnss-metadata-and-height.md](research/02-gnss-metadata-and-height.md).
- [Research coordinate conversion libraries](tickets/04-research-coordinate-conversion-libraries.md) — `mil.nga:mgrs` (plain JVM artifact, MIT, NGA-authored) for UTMREF/MGRS, formatted per the Bavarian BOS convention confirmed from an official Feuerwehrschulen Bayern document (`32U NA 648 215`, 10-digit/1 m default precision); Bavaria actually spans **four** grid zones (32T/32U/33T/33U), not two — corrects the ticket's premise; `com.google.openlocationcode` for Plus Codes; what3words drop confirmed against its own licence terms (no free/offline path for a personal app). Findings: [wayfinder/research/04-coordinate-conversion-libraries.md](research/04-coordinate-conversion-libraries.md).

## Not yet specified

- **Map interaction details** — zoom/rotate/follow-me behavior, toggling between base/satellite/parcel-overlay layers, marker/accuracy-circle rendering. Library and tile sources are now chosen ([Research map library and satellite imagery sources](tickets/01-research-map-library-and-imagery.md)); sharpens further during the main-screen prototype.
- **Offline behavior & tile caching** — whether/how tiles are cached for field use with no signal. Licensing no longer blocks this (OpenFreeMap and the Bavarian DOP CC BY 4.0 license both permit local caching); still an open product decision on whether to build it.
- **Coordinate formatting details** — lat/lon display variants (decimal vs DMS), copy/share payload formats. UTMREF precision and grouping are now decided ([Research coordinate conversion libraries](tickets/04-research-coordinate-conversion-libraries.md)); remainder sharpens during the UI prototype.
- **Permission & error UX** — first-run permission flow, GPS-off / permission-denied / no-fix states. Sharpens after the UI prototype.
- **Update cadence specifics** — exact request intervals and any adaptive throttling for the live-but-frugal requirement. Sharpens after the location-provider research.

## Out of scope

- Background location of any kind (explicit user requirement).
- Track recording, navigation/routing, POI search.
- Store or F-Droid publication, iOS/cross-platform.
- Building the app itself — execution happens after this map, against the finished spec.
