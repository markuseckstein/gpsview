# Phase 0 Research: GPSView — Live Location Viewer

**Date**: 2026-07-11 · **Status**: complete — no open unknowns

All research for this feature was performed during the wayfinder phase (see `wayfinder/research/` and `wayfinder/tickets/`) and consolidated into [SPEC.md](../../SPEC.md). The Technical Context in [plan.md](plan.md) contains **no NEEDS CLARIFICATION markers**; this document records each decision in speckit form with rationale and rejected alternatives, linking the primary evidence. Items that documentation alone could not settle are listed at the end as build-time empirical checks (SPEC.md §10) — none blocks starting.

---

## R1. Map renderer: MapLibre Native + maplibre-compose

- **Decision**: `org.maplibre.gl:android-sdk` 13.3.1 with the `org.maplibre.compose` wrapper v0.13.0. If the Compose wrapper's raster API surface proves insufficient, fall back to the classic View-based `MapView` in `AndroidView` (same native SDK underneath).
- **Rationale**: open-source (BSD), vector-tile capable, first-class raster-source support for the two WMS overlays, no API key, no usage caps, active Compose wrapper on Maven Central. ([research 01](../../wayfinder/research/01-map-library-and-imagery.md))
- **Alternatives considered**: Google Maps SDK (closed, key + ToS overhead, poor fit for WMS overlays and a FOSS-leaning personal app); osmdroid (raster-era architecture, weaker vector styling); Mapbox SDK (license/telemetry).

## R2. Base map tiles: OpenFreeMap vector tiles

- **Decision**: OpenFreeMap public instance, one of its published MapLibre style URLs (most neutral, e.g. Liberty-class; confirm exact URL at build time). Base layer always on.
- **Rationale**: no API key, no caps, self-hostable escape hatch, auto-attributed by MapLibre's attribution control. ([research 01](../../wayfinder/research/01-map-library-and-imagery.md))
- **Alternatives considered**: raw OSM raster tiles (tile-usage-policy friction, no vector styling); commercial vector hosts MapTiler/Stadia (keys, quotas — needless for a personal app).

## R3. Aerial imagery: Bavarian DOP20 orthophotos via open WMS

- **Decision**: MapLibre `raster` source over `https://geoservices.bayern.de/od/wms/dop/v1/dop20` with `{bbox-epsg-3857}`; opt-in toggle, default off; Bavaria-only coverage accepted by design. License CC BY 4.0, attribution "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de".
- **Rationale**: official, free, keyless, TLS, genuine 20 cm orthophotos of exactly the region that matters. ([research 01](../../wayfinder/research/01-map-library-and-imagery.md))
- **Alternatives considered**: Google/Bing/Esri imagery (licensing forbids or complicates MapLibre use); Sentinel-2 (resolution far too coarse for parcel-level work).

## R4. Parcel overlay: ALKIS-Parzellarkarte WMS (outline-only)

- **Decision**: raster source on `https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte`, layer `by_alkis_parzellarkarte_umr_gelb` with **mandatory** `STYLES=Gelb`, `TRANSPARENT=TRUE`, `minzoom` ≈ 16–17; opt-in, default off. Yellow outlines first; swap to `umr_schwarz` only if visual testing demands. License CC BY 4.0 **confirmed with LDBV Kundenservice** ([ticket 09](../../wayfinder/tickets/09-task-confirm-parzellarkarte-license.md)) — overrides the CC BY-ND note on the product page.
- **Rationale**: built by LDBV explicitly for overlay use, genuine alpha transparency (live-tested), single Bavaria-wide endpoint, no district/tiling logic. ([research 08](../../wayfinder/research/08-cadastral-parcel-overlay.md))
- **Alternatives considered**: labeled ALKIS-Flurkarte (paid LDBV product — out of scope); vector parcel data via WFS/ATOM (would require re-styling = re-derivation, conflicting with the unmodified-tiles provenance rule and adding heavy client work).

## R5. Position fix: FusedLocationProviderClient, high accuracy, 1 s

- **Decision**: FLP `requestLocationUpdates` wrapped in a `callbackFlow`; `PRIORITY_HIGH_ACCURACY` (explicit override — default is balanced), `setIntervalMillis(1000)`, implicit min-update default, `setMaxUpdateDelayMillis(0)` (no batching), `setWaitForAccurateLocation(true)`; `awaitClose { removeLocationUpdates }`.
- **Rationale**: matches Google's own documented "foreground mapping app" scenario; 1 s is Google's stated sufficient rate for high-rate apps; batching is a background technique that conflicts with a live display. ([research 03](../../wayfinder/research/03-location-provider-and-battery.md))
- **Alternatives considered**: raw `LocationManager.GPS_PROVIDER` (loses fused sensor blending, slower first fix); adaptive/motion-based throttling (rejected — every documented throttling technique targets background use; foreground-live is the one case it doesn't fit).

## R6. Satellite metadata: GnssStatus callback, separate from FLP

- **Decision**: `LocationManager.registerGnssStatusCallback(Executor, Callback)` (API-30 overload) in its own `callbackFlow`; visible = `satelliteCount`, used = count of `usedInFix(i)`. Satellite fields nullable on the snapshot — the GNSS flow may be silent without stalling the fix.
- **Rationale**: `Location.getExtras().getInt("satellites")` is deprecated in API 34 in favor of exactly this callback; no single API provides fix + satellite detail. FLP's high-accuracy request satisfies the GnssStatus foreground precondition, so one chipset session is expected (empirical check E4). ([research 02](../../wayfinder/research/02-gnss-metadata-and-height.md))
- **Alternatives considered**: location extras (deprecated); `GnssMeasurement` APIs (far lower-level than needed for a used/visible ratio).

## R7. Sea-level (NHN) height: platform AltitudeConverter, in-flow enrichment

- **Decision**: enrich every fix inside the `LocationSource` flow: pass through if `hasMslAltitude()`; otherwise one app-lifetime `android.location.altitude.AltitudeConverter` instance, `addMslAltitudeToLocation` on `Dispatchers.IO`, catch `IOException`/`IllegalArgumentException` → MSL stays null. Do **not** use the API-35-only `tryAddMslAltitudeToLocation`.
- **Rationale**: platform API guaranteed at min SDK 34, offline (bundled geoid), independent internal cache designed for reuse; first call may take seconds loading assets → must be off the main thread; enrichment inside the flow keeps every emitted snapshot complete. ([research 02](../../wayfinder/research/02-gnss-metadata-and-height.md), [architecture ticket 06](../../wayfinder/tickets/06-grilling-architecture.md))
- **Alternatives considered**: `androidx.core:core-location-altitude` compat shim (dead weight at min 34); shipping an own geoid model (needless duplication of a platform capability); online geoid services (violates offline principle).

## R8. UTMREF/MGRS conversion: `mil.nga:mgrs` + own BOS spacing formatter

- **Decision**: plain-JVM `mil.nga:mgrs` 2.1.3 (`MGRS.from(Point)`, `coordinate(GridType)`) for the math; GPSView ships its own thin formatter producing the Bavarian BOS grouping *Zonenfeld · 100-km-Quadrat · Ostwert · Nordwert* (`32U NA 64846 21576`). Default precision 10-digit/1 m; even digit counts only (4/6/8/10).
- **Rationale**: NGA-authored MIT library, pure Java 8+, no Android baggage; UTMREF ≡ MGRS is stated verbatim by Merkblatt 9.008; a live GPS fix justifies 1 m default (6-digit is the taught convention for hand-plotted map readings, kept one toggle away in share precision). Own formatter because the library's output grouping is unverified (empirical check E1) and the BOS format is a correctness requirement, not cosmetics. ([research 04](../../wayfinder/research/04-coordinate-conversion-libraries.md))
- **Alternatives considered**: `mil.nga.mgrs:mgrs-android` (exists only to add Google-Maps tile rendering, drags in `play-services-maps` — dead weight); hand-rolling UTM math (error-prone around zone/band edges — exactly where Bavaria sits: 32T/32U/33T/33U meet near 12°E/48°N).

## R9. Plus Codes: Google's openlocationcode library

- **Decision**: `com.google.openlocationcode:openlocationcode` 1.0.4; always the full global code (`8FWH4HX8+9C`), never the locality short form.
- **Rationale**: reference implementation, Apache-2.0, offline by construction; short codes need a reference locality — needless ambiguity. ([research 04](../../wayfinder/research/04-coordinate-conversion-libraries.md))
- **Alternatives considered**: none serious — this is the canonical artifact.

## R10. what3words: excluded

- **Decision**: not included in v1 (or any planned version); Plus Codes fill the "speakable short code" niche.
- **Rationale**: no free/offline path exists for a personal app — confirmed against w3w's own license terms. ([research 04 §4](../../wayfinder/research/04-coordinate-conversion-libraries.md))

## R11. Architecture: single module, three packages, manual DI

- **Decision**: one Gradle `:app` module, package-layered `coordinates` (pure) / `data` (Android, behind `LocationSource`/`GnssSource` interfaces) / `ui` (one Activity, one ViewModel). Manual constructor injection with a `ViewModelProvider.Factory`; no Hilt. `PositionSnapshot` holds raw sensor truth only; all coordinate representations are derived at UI-state build time. ViewModel `combine`s the two flows into `StateFlow<PositionUiState>`; `MapUiState` is a separate flow. Copy/share: pure layer builds strings, Composable performs the Android act — no ViewModel event channel.
- **Rationale**: the correctness that matters (grid reference read aloud in the field) is deterministic math — isolating it as pure JVM code makes it exhaustively testable without a device; everything else is deliberate ceremony-avoidance for a one-screen personal app. ([ticket 06](../../wayfinder/tickets/06-grilling-architecture.md))
- **Alternatives considered**: second Gradle module for the core (rejected — promoting a package later is cheap); Hilt (ceremony for one Activity/one ViewModel); storing formatted strings on the snapshot (rejected — derivation belongs in one place, display prefs would leak into sensor data).

## R12. Foreground-only mechanism: lifecycle-scoped flow collection

- **Decision**: both sources are cold `callbackFlow`s; Compose collects the ViewModel's `StateFlow` via `collectAsStateWithLifecycle()` (STARTED-scoped). Collection cancels on `ON_STOP` → `awaitClose` unregisters FLP and GnssStatus immediately. Manifest: `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` + `INTERNET`; **never** `ACCESS_BACKGROUND_LOCATION`.
- **Rationale**: the entire privacy/battery contract enforced structurally by lifecycle, not manual observer bookkeeping — it cannot be quietly eroded. ([research 03](../../wayfinder/research/03-location-provider-and-battery.md))
- **Alternatives considered**: manual register/unregister in lifecycle callbacks (bookkeeping that rots); foreground service (explicitly out of scope — background-oriented).

## R13. UX decisions: Layout A and residual conventions

- **Decision**: Layout A „Karte im Fokus" — full-bleed map, top bar with status chip + single Teilen action, bottom sheet with UTMREF + both heights in the peek, lat/lon + Plus Code + accuracy/satellite strip when expanded. Tap-to-copy on whole rows with toast; monospaced tabular figures for coordinates. First-fix zoom ~z16; follow-me default on (drag disengages, locate re-engages); rotation/tilt locked north-up; blue-dot + accuracy circle, no heading cone. Permission flow per SPEC.md §7.1 (immediate system prompt, rationale card on re-askable denial, settings deep-links, non-blocking coarse-grant banner). Copy payloads and the German share block exactly per SPEC.md §6.5–6.6.
- **Rationale**: chosen from three prototyped variants ([prototype ticket 05](../../wayfinder/tickets/05-prototype-main-screen.md), artifact `wayfinder/prototypes/main-screen-prototype.html`); residual decisions grilled and settled in [ticket 10](../../wayfinder/tickets/10-grilling-residual-ux-decisions.md).
- **Alternatives considered**: two other prototyped layouts (data-first list, split view) — rejected for burying the map; per-row share icons (rejected — one Share action, rows copy); `geo:` share URI (rejected — `https` Google Maps link is tappable in every messenger).

---

## Empirical checks carried into the build (SPEC.md §10)

Documentation could not settle these; each becomes a verification task during implementation. None blocks starting; each blocks calling its area done.

| # | Check | Consequence if surprising |
|---|---|---|
| E1 | Does `mil.nga:mgrs` emit spaced or compact strings? | None for scope — own BOS formatter required regardless; determines what it wraps |
| E2 | `mil.nga:mgrs` (Apr 2024 release) resolves/builds under current AGP | Pin adjustment or shading — verify early |
| E3 | Does FLP already populate `hasMslAltitude()`? | Enrichment path becomes rare fallback; code it anyway |
| E4 | One GNSS chipset session with FLP + GnssStatus both registered (Energy Profiler, single `onFirstFix`) | If doubled: investigate registration ordering |
| E5 | `ON_STOP` teardown is instant on the target device | Hard requirement — must hold; fix wiring if not |
| E6 | maplibre-compose exposes raster sources + per-layer visibility | Else switch map composable to `AndroidView` fallback (R1) |
| E7 | Parcel outline color: yellow vs black over vector base and orthophoto | Swap `LAYERS`/`STYLES` pair to `umr_schwarz`/`Schwarz` |
| E8 | NHN value plausible against a known Bavarian elevation | If wildly off: inspect enrichment path/units |
