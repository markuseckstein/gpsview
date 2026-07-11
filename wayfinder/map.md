---
name: gpsview-map
labels: [wayfinder:map]
status: closed
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
- **Cadastral parcels (Flurstücke):** show parcel boundary lines as an optional overlay, using only Bavaria's free ALKIS-Parzellarkarte (licensed **CC BY 4.0** — confirmed with LDBV Kundenservice, see [Confirm the ALKIS-Parzellarkarte's actual license with LDBV](tickets/09-task-confirm-parzellarkarte-license.md)). No Flurstücksnummer/Flurnummer labels — those require a paid LDBV/GeodatenOnline license, which conflicts with the sideload/loosest-licensing stance, so labeling is out of scope for now. Decided 2026-07-11.

**Tracker conventions (local markdown):** tickets are files in `wayfinder/tickets/`; frontmatter carries `status` (open/closed), `assignee` (the claim — empty means unclaimed), `labels`, and `blocked-by` (list of ticket file names). A ticket is on the frontier when open, unassigned, and everything in its `blocked-by` is closed. Resolutions are appended to the ticket file under `## Resolution`.

## Decisions so far

<!-- one line per closed ticket: gist + link -->

- [Research map library and satellite imagery sources](tickets/01-research-map-library-and-imagery.md) — MapLibre Native + `maplibre-compose` for the map library; OpenFreeMap (vector, uncapped, no key) for the base map layer; Bavarian DOP WMS (CC BY 4.0, unauthenticated) for the satellite layer. Findings: [wayfinder/research/01-map-library-and-imagery.md](research/01-map-library-and-imagery.md).
- [Research Bavarian cadastral parcel overlay integration](tickets/08-research-cadastral-parcel-overlay.md) — free ALKIS-Parzellarkarte WMS as a third MapLibre raster layer (outline-only style, transparent PNG, toggled independently, Bavaria-wide single endpoint); license text conflicts between sources (CC BY 4.0 vs CC BY-ND 4.0), flagged as a follow-up task. Findings: [wayfinder/research/08-cadastral-parcel-overlay.md](research/08-cadastral-parcel-overlay.md).
- [Confirm the ALKIS-Parzellarkarte's actual license with LDBV](tickets/09-task-confirm-parzellarkarte-license.md) — **CC BY 4.0** confirmed by LDBV Kundenservice, resolving the CC BY-ND conflict flagged by research 08.
- [Research GNSS metadata and height APIs](tickets/02-research-gnss-metadata-and-height.md) — `Location`/`GnssStatus` platform APIs for all metadata (horizontal/vertical/MSL accuracy, satellites visible vs used-in-fix, ellipsoidal + MSL height); MSL support (`hasMslAltitude`, `AltitudeConverter`) is API 34+ platform, *not* Play services as assumed — corrects the ticket's premise. Findings: [wayfinder/research/02-gnss-metadata-and-height.md](research/02-gnss-metadata-and-height.md).
- [Research coordinate conversion libraries](tickets/04-research-coordinate-conversion-libraries.md) — `mil.nga:mgrs` (plain JVM artifact, MIT, NGA-authored) for UTMREF/MGRS, formatted per the Bavarian BOS convention confirmed from an official Feuerwehrschulen Bayern document (`32U NA 648 215`, 10-digit/1 m default precision); Bavaria actually spans **four** grid zones (32T/32U/33T/33U), not two — corrects the ticket's premise; `com.google.openlocationcode` for Plus Codes; what3words drop confirmed against its own licence terms (no free/offline path for a personal app). Findings: [wayfinder/research/04-coordinate-conversion-libraries.md](research/04-coordinate-conversion-libraries.md).
- [Prototype the main screen layout](tickets/05-prototype-main-screen.md) — Layout **A „Karte im Fokus“**: map is the hero; a bottom sheet whose collapsed *peek* always shows the primary UTMREF grid ref + both heights (ellipsoidisch & NHN), pulling up to reveal lat/lon, Plus Code, accuracy and the `18/24` satellite ratio. Tap-a-row-to-copy; one position-level Share in the app bar (no per-row icon pairs). Prototype/Artifact linked from the ticket.
- [Research location provider strategy and battery behavior](tickets/03-research-location-provider-and-battery.md) — `FusedLocationProviderClient` for the position fix (not raw `GPS_PROVIDER`), running alongside `GnssStatus` registration for satellite/height metadata; `PRIORITY_HIGH_ACCURACY` with a 1–2s interval, no batching, no adaptive throttling (not a documented pattern for foreground-live display); `DisposableEffect`+`LifecycleEventObserver` on `ON_START`/`ON_STOP` for instant stop-when-backgrounded; manifest needs both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`, explicitly no background permission. Findings: [wayfinder/research/03-location-provider-and-battery.md](research/03-location-provider-and-battery.md).
- [Settle app architecture and module structure](tickets/06-grilling-architecture.md) — single Gradle `:app` module, package-layered around one seam: a **pure Android-free `coordinates` package** (converters + share-string builders, JVM-unit-tested) vs an Android `data` package (FLP/`GnssStatus` as `callbackFlow`s behind `LocationSource`/`GnssSource` interfaces, MSL enriched inside the location flow). Snapshot holds **only raw sensor truth**; all coordinate representations are pure derived projections. State = sealed `PositionUiState` (`NotYetAsked`/`PermanentlyDenied`/`LocationOff`/`Acquiring`/`Live`) with the two-level absence rule (missing *fix* → top-level state, missing *field* → nullable on the snapshot); a **separate** `MapUiState` (layer toggles, camera, follow-me-gates-camera). Two flows `combine`d in a ViewModel, `collectAsStateWithLifecycle` + `repeatOnLifecycle(STARTED)`; manual constructor injection (no DI framework); copy/share strings built in the pure layer, clipboard/intent fired straight from the Composable. Full detail in the resolution.
- [Settle residual UX and product decisions](tickets/10-grilling-residual-ux-decisions.md) — lat/lon **decimal-default with a DMS toggle**; per-row copy is verbatim except **decimal lat/lon normalizes to `48.137154, 11.575382`** (dotted, paste-ready); **Share** is a full German block (UTMREF first, both heights, accuracy, timestamp) capped with an `https` Google Maps link; **permissions requested immediately with no pre-rationale**, permanent-denial and location-off deep-link to settings, coarse-only grant functions-but-warns; **map defaults** = ~z16 first fix, follow-me on (drag disengages), **north-up locked**, **vector basemap default** with satellite/parcel as opt-in toggles, blue-dot + accuracy circle. Offline tile caching ruled out of v1 (see Out of scope). Full detail in the resolution.
- [Assemble the implementation-ready spec](tickets/07-task-assemble-spec.md) — **destination reached:** every decision above consolidated into [SPEC.md](../SPEC.md) at the repo root, with per-section links back to its source tickets/research. Nothing invented; the eight research-flagged empirical smoke-test items are carried in the spec's §10.

## Not yet specified

_Empty — the fog is fully cleared and the destination is reached._ All tickets are closed; the implementation-ready spec is [SPEC.md](../SPEC.md). Execution against it is a fresh effort beyond this map.

## Out of scope

- Background location of any kind (explicit user requirement).
- Track recording, navigation/routing, POI search.
- Store or F-Droid publication, iOS/cross-platform.
- Building the app itself — execution happens after this map, against the finished spec.
- **Offline tile caching** — deferred from v1 (not forbidden): online basemap only, while all coordinate readouts keep working offline. Ruled out to keep v1 tractable; cleanly additive later. Decided in [Settle residual UX and product decisions](tickets/10-grilling-residual-ux-decisions.md), 2026-07-11.
