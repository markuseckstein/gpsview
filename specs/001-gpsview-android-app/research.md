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
| E1 | Does `mil.nga:mgrs` emit spaced or compact strings? | **Resolved 2026-07-11 (T018)**: compact, no delimiters (e.g. `32UNA6484621576`) — confirmed by running the pinned 2.1.3 jar directly (`MGRS.from(lon, lat).coordinate(GridType)`). GPSView's own BOS formatter wraps it as designed. |
| E2 | `mil.nga:mgrs` (Apr 2024 release) resolves/builds under current AGP | **Resolved 2026-07-11**: `./gradlew assembleDebug` and `testDebugUnitTest` both succeed. Build-time version pins settled empirically (not fully "latest" — see note below): AGP 8.13.2, Gradle 9.5.1, Kotlin 2.3.21, compileSdk/targetSdk 36. `mil.nga:mgrs` 2.1.3 + `mil.nga:grid` 1.1.2 resolve and compile without shading. |
| E3 | Does FLP already populate `hasMslAltitude()`? | **Observed 2026-07-11 (T024)**: not distinguished by direct instrumentation, but on-device the ellipsoidal/NHN split was populated within ~4 s of first fix with a ~47 m separation (472 m ellipsoidal vs. 425 m NHN) — a plausible Bavarian geoid undulation, and fast enough that either FLP supplied it natively or the `AltitudeConverter` fallback's geoid-asset load was not a perceptible delay. Either path produces correct output; not fully disambiguated. |
| E4 | One GNSS chipset session with FLP + GnssStatus both registered (Energy Profiler, single `onFirstFix`) | **Resolved 2026-07-11 (T014)**: verified via `adb logcat` on a physical Samsung SM-A137F (Android 14) instead of Energy Profiler (CLI-only environment, no Android Studio GUI available). `Gnss: updateWakelock: GNSS HAL Wakelock acquired due to gps: 1, fused: 0` confirms a single chipset session backs both the FLP high-accuracy request and our own `GnssStatusSource` registration — not doubled. |
| E5 | `ON_STOP` teardown is instant on the target device | **Resolved 2026-07-11 (T014), PASS**: same device/method. Backgrounding via `KEYCODE_HOME` shows `WindowStopped...set to true` at 21:14:21.906 essentially coincident (within ~20ms, preceding) with `LocationManagerService: gps provider removed registration` and `GnssLocationProvider: stopNavigating` at 21:14:21.885–887 — teardown is near-instant, well under the 1 s requirement. Re-foregrounding immediately shows `gps provider added registration` + `startNavigating` again, confirming the full stop/resume cycle via `collectAsStateWithLifecycle()` works as designed. |
| E6 | maplibre-compose exposes raster sources + per-layer visibility | **Resolved 2026-07-11 (T025), YES — no `AndroidView` fallback needed**: read the pinned 0.13.0 source directly (`org.maplibre.compose.sources.RasterSource`/`rememberRasterSource(tiles, options, tileSize)`, `org.maplibre.compose.layers.RasterLayer(id, source, minZoom, maxZoom, visible, …)`); `visible` is a first-class per-layer param, `TileSetOptions.attributionHtml` lets each WMS source carry its own attribution shown by the built-in attribution control, and `CameraMoveReason.GESTURE` (vs. `PROGRAMMATIC`) on `CameraState.moveReason` cleanly distinguishes user drags from programmatic camera moves — exactly what follow-me disengage needs. Base style confirmed from the library's own docs snippet: OpenFreeMap's Liberty style at `https://tiles.openfreemap.org/styles/liberty`. |
| E7 | Parcel outline color: yellow vs black over vector base and orthophoto | **Resolved 2026-07-11 (T041), yellow (`umr_gelb`/`Gelb`) is legible — kept, no swap needed**: verified on-device over the vector base (clearly legible against beige/white building fill); not separately re-checked layered directly over DOP20 imagery in this session, but the vector-base result was unambiguous enough not to warrant the `umr_schwarz`/`Schwarz` swap. |
| E8 | NHN value plausible against a known Bavarian elevation | **Resolved 2026-07-11 (T024), PASS**: on-device fix near the developer's location (49.35 N, 11.32 E) showed 472 m ellipsoidal / 425 m NHN — 47 m undulation is squarely within Bavaria's known geoid-separation range. |

**T048 battery/lifecycle re-verification (2026-07-11)**: re-ran the E4/E5 checks with the complete app (map, both Bavarian overlays toggled on, bottom sheet) instead of the Phase 2 skeleton. Backgrounding via `KEYCODE_HOME` still shows `gps provider removed registration` + `GnssLocationProvider: stopNavigating` within the same ~50 ms window as `WindowStopped...set to true` — teardown timing is unaffected by the added UI/map load. Re-foregrounding shows `gps provider added registration` + a single `GNSS HAL Wakelock acquired due to gps: 1, fused: 0` — still exactly one chipset session. Both E4 and E5 hold under full load.

**V7/V8 on-device validation (2026-07-11, T046)**: verified the full permission/system-state matrix on the physical device via `adb`/`pm grant`/`pm revoke`/screenshots.

- Fresh install (no permission yet): system dialog fires **immediately, no pre-rationale card** — confirmed.
- First denial ("Nicht erlauben"): rationale card ("Standortzugriff benötigt" + "Erneut versuchen") renders correctly over the map.
- Second denial (Android auto-switches to "deny and don't ask again"): `PermanentlyDenied` screen renders, deep-links to `ACTION_APPLICATION_DETAILS_SETTINGS` — confirmed the button actually opens the real App-Info settings screen.
- **Bug found and fixed**: coarse-only grant (`ACCESS_COARSE_LOCATION` only) crashed the app outright ("GPSView wird wiederholt beendet"). Root cause: `LocationManager.registerGnssStatusCallback` requires `ACCESS_FINE_LOCATION` specifically (confirmed via the crash's `SecurityException` from `GnssManagerService`) — unlike FLP fixes, which work fine on coarse. `GnssStatusSource`'s existing `catch (e: SecurityException) { close(e) }` propagated that exception through the ViewModel's `combine()` and crashed the whole position pipeline, not just the satellite sub-flow. Fixed in `GnssStatusSource.kt`: the catch now leaves the flow silently non-emitting (per its own contract — "may never emit; must never error the UI") instead of closing with an error. Re-verified on-device: coarse-only grant now functions normally (fix still appears, less precise) with the persistent "Genauer Standort empfohlen" banner and working "Genauen Standort anfragen" button, no crash.
- `LocationOff`: card + dimmed map verified, deep-link to `ACTION_LOCATION_SOURCE_SETTINGS` opens the real system Location settings screen; toggling location back on and returning to the app correctly resumed to `Acquiring` (ON_START re-evaluation, ViewModel test-covered in T042).
- About/Licenses (V8): all 8 entries render with correct license names and attribution text, matching contracts/map-services.md exactly.
- Not exercised: re-requesting precise location after FINE had already been UI-denied twice earlier in the *same* test session — Android silently no-ops the redialog in that case (an artifact of `pm grant`/`pm revoke` not clearing the OS's internal "asked before" flag the way a fresh install would), not a real bug; the coarse-only banner correctly stayed non-blocking throughout.

**V6 on-device validation (2026-07-11, T041)**: verified on the physical device. DOP20 real 20 cm aerial imagery renders correctly when "Satellit" is toggled; ALKIS parcel outlines (yellow) render aligned with base-map building/road geometry when "Flurstücke" is toggled, both independently switchable and both default off. The `minzoom=16` gate on the parcel layer holds (parcels rendered at the first-fix z16 camera). **Finding and fix**: MapLibre's built-in attribution dialog does not surface a WMS raster source's `TileSetOptions.attributionHtml` on Android — confirmed by toggling DOP20 on and opening the attribution "ⓘ" dialog, which listed only "MapLibre Android / OpenFreeMap / © OpenMapTiles / OpenStreetMap" with no Bavarian line. Templated `GetMap` WMS URLs aren't real TileJSON sources with an attribution manifest the control reads, apparently. Fixed by adding an explicit small attribution overlay in `MapContent.kt`, shown whenever either Bavarian layer is visible — re-verified on-device, "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" now renders correctly for both layers. Outside-Bavaria blank-tile behavior and WMS-failure graceful degradation were not separately exercised (would require traveling outside Bavaria or simulating a WMS outage) — architecturally sound by construction (raster layers fail independently of the base map; not further verified this session).

**V4 on-device validation (2026-07-11, T036)**: verified on the physical device. Tapping the UTMREF hero row copied it to the clipboard and showed the German confirmation toast "UTMREF kopiert"; the Teilen action opened the standard Android share sheet with a correct preview ("Standort (GPSView) / UTMREF: 32U PV 68190 69248…") and real target apps (WhatsApp, Messages, Quick Share, etc.).

**V5 on-device validation (2026-07-11, T031)**: verified on the physical device. First fix centred the camera at z16 with the blue-dot puck at the correct real-world position (street/POI labels matched the developer's actual location). A swipe-drag panned the camera and — confirmed via `CameraMoveReason.GESTURE` — correctly disengaged follow-me while the bottom-sheet readout kept updating live underneath, decoupled from camera state. Tapping the locate FAB re-centred on the current position and re-engaged follow-me. Rotation/tilt gestures are structurally disabled (`GestureOptions.RotationLocked`). Attribution renders via MapLibre's built-in control (tap-to-expand "ⓘ"). **V3 (offline coordinate truth) could not be exercised from this environment**: enabling airplane mode over `adb` requires a broadcast (`android.intent.action.AIRPLANE_MODE`) that a non-rooted device's shell user is denied (`SecurityException`, uid=2000) — there is no unprivileged `adb shell` path to toggle radios. Architecturally this should hold regardless (no code path in `coordinates`/`LocationSource`/`GnssSource` touches the network — only `MapContent.kt`'s base-style tile fetch does), but the user should confirm V3 by hand per quickstart.md.

**V1 on-device validation (2026-07-11, T024)**: verified on the physical Samsung SM-A137F via `adb`/screenshots (indoors near a window, not a literal outdoor open-sky test — genuine GNSS fix nonetheless, 10–12/39 satellites used/visible, ±1–5 m accuracy). Fix appeared within ~4 s of launch (well under the 30 s SC-001 target); UTMREF/height/lat-lon values visibly updated across consecutive screenshots seconds apart; decimal⇄DMS toggle verified live (`49° 21′ 9,49″ N / 11° 18′ 57,73″ O` ⇄ `49,352722° / 11,316105°`); satellite ratio + fill bar rendered correctly; sheet peek/expand verified via swipe gesture. True outdoor first-fix timing and continuous-walking refresh cadence should still be confirmed in the field by the user; the mechanism itself is proven live.

**Fixture correction (2026-07-11, T015/T017)**: contracts/coordinates-api.md's Plus Code fixture (`48.137154, 11.575382 → 8FWH4HX8+9C`) does not match the pinned `com.google.openlocationcode` 1.0.4 reference implementation, run directly against that input: it produces `8FWH4HPG+V5` (verified via both the instance constructor and the static `encode()` method, and a decode round-trip landing within one cell of the input). The contract's value appears to be a hand-typed example rather than a library-frozen one. Tests use the library-verified `8FWH4HPG+V5`; the illustrative Munich UTMREF in the share-block example (`32U PU 034 926`) has the same issue and is replaced with the library-computed `32U PU 915 347` (6-digit) / `32U PU 91595 34752` (10-digit).

**Build tooling note (2026-07-11, T004)**: "latest stable at build time" for AGP/Gradle/androidx turned out to be a moving target with a hard edge: AGP 9.0+ ships built-in Kotlin support and drops the separate `org.jetbrains.kotlin.android` plugin, and the very latest androidx releases (`core-ktx` 1.19.0, `lifecycle` 2.11.0, `activity-compose` 1.13.0) already require `compileSdk` 37 / AGP ≥ 9.1.0. Rather than adopt AGP 9's brand-new built-in-Kotlin migration sight-unseen for a project that prizes radical simplicity (constitution V), this build pins to the last androidx releases still compatible with `compileSdk` 36 on the well-established AGP 8.x + explicit Kotlin/Compose-compiler-plugin path: `core-ktx` 1.18.0, `lifecycle-*` 2.10.0, `activity-compose` 1.12.4, AGP 8.13.2, Gradle 9.5.1 (AGP 8.x is incompatible with Gradle ≥ 9.6 — an internal API AGP relies on was removed). This is a deliberate, reasoned pin, not a silent drift; revisit if a future task needs an androidx feature gated behind the newer releases.
