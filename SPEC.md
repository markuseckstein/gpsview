# GPSView — Implementation Spec

**Status:** implementation-ready. Assembled 2026-07-11 from the decisions on the [Wayfinder map](wayfinder/map.md); every choice below links its source ticket/research. A single build effort should need no further decisions.

GPSView is a **personal, sideload-only Android app** that shows live details of the current location: coordinates in multiple systems (lat/lon, UTMREF/MGRS as used by Bavarian fire services, Plus Codes), GNSS metadata (accuracy, satellite counts, ellipsoidal **and** sea-level height), and the position on an open-source map with optional satellite imagery and an optional overlay of Bavarian cadastral parcel boundaries (Flurstücke). It is battery-frugal and **strictly foreground-only**: all location activity stops the instant the app is no longer visible.

---

## 1. Constraints and non-goals

Standing constraints ([map Notes](wayfinder/map.md)):

- **Stack:** Kotlin + Jetpack Compose. **min SDK 34** (Android 14+). Google Play services allowed.
- **Distribution:** personal sideload only. No Play Store / F-Droid.
- **UI language: German** throughout (labels, states, toasts, share text). Decimal commas in *displayed* numbers (copy payloads differ — §6.2).
- **Foreground-only:** live updates while the app is visible; every location registration is torn down on `ON_STOP`. No background permission, no foreground service.

**Out of scope for v1** (from the map's [Out of scope](wayfinder/map.md) and [ticket 10](wayfinder/tickets/10-grilling-residual-ux-decisions.md)):

- Background location of any kind.
- Track recording, navigation/routing, POI search.
- **Offline tile caching** — deferred, not forbidden. v1 is **online-basemap-only**: with no connectivity the map shows blank/last-rendered tiles while **all coordinate readouts keep working** (they are computed on-device from the GNSS fix). Cleanly additive later.
- **what3words** — no free/offline path exists for a personal app (confirmed against w3w's own licence terms, [research 04 §4](wayfinder/research/04-coordinate-conversion-libraries.md)). Plus Codes replace it.
- **Flurstücksnummer/Flurnummer labels** on the parcel overlay — the labeled ALKIS-Flurkarte is a paid LDBV product. Boundaries only.
- Gauß-Krüger coordinates (officially deprecated for Bavarian BOS since 2019/2020, [research 04 §2.4](wayfinder/research/04-coordinate-conversion-libraries.md)).
- Heading cone on the position marker; per-constellation satellite breakdown on the main screen.

---

## 2. Dependencies

| Purpose | Artifact | Version (pin at build time) | License | Source |
|---|---|---|---|---|
| Map renderer | `org.maplibre.gl:android-sdk` | 13.3.1 | BSD-2-Clause | [research 01](wayfinder/research/01-map-library-and-imagery.md) |
| Compose map wrapper | `org.maplibre.compose` (maplibre-compose, Maven Central) | v0.13.0 | BSD-3-Clause | [research 01](wayfinder/research/01-map-library-and-imagery.md) |
| UTMREF/MGRS | `mil.nga:mgrs` | 2.1.3 | MIT | [research 04](wayfinder/research/04-coordinate-conversion-libraries.md) |
| (transitive) grid math | `mil.nga:grid` | 1.1.2 | MIT | [research 04](wayfinder/research/04-coordinate-conversion-libraries.md) |
| Plus Codes | `com.google.openlocationcode:openlocationcode` | 1.0.4 | Apache-2.0 | [research 04](wayfinder/research/04-coordinate-conversion-libraries.md) |
| Fused location | `com.google.android.gms:play-services-location` | latest stable | Google ToS | [research 03](wayfinder/research/03-location-provider-and-battery.md) |
| Lifecycle/Compose | AndroidX (lifecycle ≥ 2.8.0 for `repeatOnLifecycle`/`collectAsStateWithLifecycle`) | latest stable | Apache-2.0 | [research 03](wayfinder/research/03-location-provider-and-battery.md) |

**Deliberate exclusions:**

- **Not** `mil.nga.mgrs:mgrs-android` — it exists only to add Google-Maps tile-overlay rendering and drags in `play-services-maps`, dead weight for a MapLibre app. Use the plain JVM `mil.nga:mgrs` artifact (pure Java 8+, runs fine on Android).
- **No `AltitudeConverterCompat`** (`androidx.core:core-location-altitude`) — min SDK 34 means the platform `android.location.altitude.AltitudeConverter` is always available ([research 02 §4](wayfinder/research/02-gnss-metadata-and-height.md)).
- **No DI framework** (Hilt rejected as ceremony for a one-Activity/one-ViewModel app — [architecture](wayfinder/tickets/06-grilling-architecture.md)).
- If `maplibre-compose`'s API surface turns out to miss something (fine-grained raster-layer control), the sanctioned fallback is the classic View-based `MapView` wrapped in Compose's `AndroidView` — same underlying native SDK.

---

## 3. Module and package structure

**Single Gradle `:app` module**, package-layered ([architecture](wayfinder/tickets/06-grilling-architecture.md)). A second module was considered and rejected; promoting a package to a module later is cheap.

```
:app
 ├─ coordinates/   ← PURE. Zero Android imports. The testability seam.
 │    LatLon, PositionSnapshot,
 │    UTMREF/MGRS converter + BOS-spacing formatter,
 │    Plus Code converter, lat/lon formatters (decimal & DMS),
 │    ShareFormatting (copy payloads + share block builder)
 ├─ data/          ← Android-dependent.
 │    LocationSource (interface) ← FusedLocationProviderClient callbackFlow,
 │                                  MSL enrichment inside the flow
 │    GnssSource (interface)     ← LocationManager.registerGnssStatusCallback callbackFlow
 └─ ui/            ← Compose + ViewModel (one Activity, one ViewModel).
```

The rule that makes this work: **`coordinates` is pure `input → String`/value functions**, unit-tested with plain JUnit on the JVM — no Robolectric, no device. The `LocationSource`/`GnssSource` **interfaces are the enforced boundary** keeping Android out of the merge logic; tests pass fakes.

---

## 4. Data layer

### 4.1 Position fix — `LocationSource`

Wrap `FusedLocationProviderClient.requestLocationUpdates` in a `callbackFlow` ([research 03](wayfinder/research/03-location-provider-and-battery.md)):

- **Priority: `Priority.PRIORITY_HIGH_ACCURACY`** — matches Google's own "user visible or foreground updates / mapping app" scenario. Note the default is `BALANCED_POWER_ACCURACY`; the override is mandatory.
- **`setIntervalMillis(1000)`** (1 s; up to 2000 ms acceptable — Google's Builder doc: "an interval of 1 second is likely sufficient for the vast majority of 'high location rate' applications").
- **`setMinUpdateIntervalMillis`: leave at implicit default** (half the interval).
- **`setMaxUpdateDelayMillis(0)`** — no batching; batching is a background technique that directly conflicts with a live display.
- **`setWaitForAccurateLocation(true)`** (the default) — avoids flashing a low-accuracy first fix.
- **No adaptive/motion-based throttling.** Considered and rejected: not a documented pattern for foreground-live display; every throttling technique Google documents targets background use.
- `awaitClose { removeLocationUpdates(...) }` is the teardown.

### 4.2 MSL enrichment — inside the location flow

Every fix emitted by `LocationSource` is already complete ([architecture](wayfinder/tickets/06-grilling-architecture.md), [research 02](wayfinder/research/02-gnss-metadata-and-height.md)):

1. If `location.hasMslAltitude()` is already true (provider populated it), pass through.
2. Otherwise run it through a **single app-lifetime `android.location.altitude.AltitudeConverter` instance** (the class doc: it "manages an independent cache" — reuse, don't reconstruct): `addMslAltitudeToLocation(context, location)` **off the main thread** (`Dispatchers.IO`; the call may take seconds on first use while loading its bundled geoid asset). Catch `IOException`/`IllegalArgumentException` → leave MSL null.
3. Re-check `hasMslAltitude()`/`hasMslAltitudeAccuracy()` after conversion; map to nullable snapshot fields.

`AltitudeConverter` is a **platform API 34+ class, not Play services**, and is offline-capable (bundled geoid data, no network). The API-35-only `tryAddMslAltitudeToLocation` overload must **not** be used (min SDK is 34).

### 4.3 Satellite metadata — `GnssSource`

Wrap `LocationManager.registerGnssStatusCallback(Executor, GnssStatus.Callback)` (the API-30 overload) in a `callbackFlow` ([research 02](wayfinder/research/02-gnss-metadata-and-height.md)):

- **Visible** = `status.satelliteCount`.
- **Used in fix** = `(0 until status.satelliteCount).count { status.usedInFix(it) }`.
- **Never** read `Location.getExtras().getInt("satellites")` — deprecated in API 34 in favor of exactly this mechanism.
- This subsystem is architecturally separate from FLP — no single API provides both; both registrations run concurrently. The FLP high-accuracy request satisfies the GnssStatus precondition ("while GPS_PROVIDER is enabled… in the foreground"), so no duplicate chipset activation is expected (smoke-test item, §10).
- The GNSS flow may be absent/silent without stalling the fix: satellite fields are nullable on the snapshot.

### 4.4 Lifecycle — instant stop when backgrounded

The two cold flows are collected only while the lifecycle is `STARTED`: Compose consumes the ViewModel's `StateFlow` via **`collectAsStateWithLifecycle()`** (which uses `repeatOnLifecycle(Lifecycle.State.STARTED)` internally). Collection cancels on `ON_STOP` → the `callbackFlow`s' `awaitClose` blocks unregister FLP and GnssStatus immediately. This is the entire foreground-only mechanism — no service, no manual observer bookkeeping.

### 4.5 Manifest permissions

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Both, exactly as the official permissions guide's annotated snippet declares them. **Do not declare `ACCESS_BACKGROUND_LOCATION`.** Also `android.permission.INTERNET` for tile fetching.

---

## 5. Domain and state model

### 5.1 `PositionSnapshot` — raw sensor truth only

The snapshot holds **only what the sensors reported**; no coordinate representation is stored ([architecture](wayfinder/tickets/06-grilling-architecture.md)):

| Field | Type | Source & guard |
|---|---|---|
| `latLon` | `LatLon` (WGS84) | `location.latitude/longitude` |
| `horizontalAccuracyM` | `Float?` | `getAccuracy()` iff `hasAccuracy()` — 68th-percentile radius |
| `ellipsoidalAltitudeM` | `Double?` | `getAltitude()` iff `hasAltitude()` — always WGS84-ellipsoid |
| `verticalAccuracyM` | `Float?` | `getVerticalAccuracyMeters()` iff `hasVerticalAccuracy()` |
| `mslAltitudeM` | `Double?` | `getMslAltitudeMeters()` iff `hasMslAltitude()` (after §4.2 enrichment) |
| `mslAccuracyM` | `Float?` | `getMslAltitudeAccuracyMeters()` iff `hasMslAltitudeAccuracy()` |
| `timestamp` | epoch millis | `location.time` |
| `satellites` | `SatelliteCount?` (`used`, `visible`) | GnssSource; null until first status arrives |

UTMREF/MGRS, Plus Code, and formatted lat/lon are **pure derived projections** computed by the `coordinates` package when UI state is built.

### 5.2 `PositionUiState` — sealed screen states

**Two-level absence rule:** absence of a **fix** is a top-level state; absence of a **field** is a nullable on the snapshot, rendered as a single dashed row. This keeps `Live` rendering total.

```kotlin
sealed interface PositionUiState {
    data object NotYetAsked : PositionUiState        // permission not yet requested
    data object PermanentlyDenied : PositionUiState  // denied with "don't ask again"
    data object LocationOff : PositionUiState        // system location setting off
    data object Acquiring : PositionUiState          // permission granted, no fix yet
    data class Live(val snapshot: PositionSnapshot) : PositionUiState
}
```

### 5.3 `MapUiState` — separate flow

A **distinct `StateFlow`** owned by the same ViewModel (position and map state change for different reasons at different rates):

- Layer toggles: `satelliteVisible: Boolean` (default **false**), `parcelsVisible: Boolean` (default **false**). Base vector layer always on.
- Camera position; `followMe: Boolean` (default **true**).
- **Follow-me gates the camera:** the map Composable moves the camera to a new fix only while `followMe` is true. Dragging the map disengages it; the locate button re-engages and recentres.
- The lat/lon **decimal ⇄ DMS toggle** is a display preference in UI state, not a snapshot field.

### 5.4 Update flow and wiring

- The ViewModel `combine`s the two source flows into `StateFlow<PositionUiState>`; "latest of each" comes free from `combine`.
- **Manual constructor injection:** the ViewModel takes `LocationSource`/`GnssSource` via constructor; a `ViewModelProvider.Factory` wires the real implementations; tests pass fakes.
- **Copy/share:** the pure `coordinates` layer builds the string; the Composable performs the Android act directly (`ClipboardManager.setPrimaryClip` / `ACTION_SEND` chooser). No ViewModel event channel.

---

## 6. Coordinate formats

### 6.1 UTMREF/MGRS — the hero format

UTMREF and MGRS are synonymous (stated verbatim by the Bavarian fire-service Merkblatt 9.008 — [research 04 §2](wayfinder/research/04-coordinate-conversion-libraries.md)). Display in the **Bavarian BOS convention**: space-separated groups *Zonenfeld · 100-km-Quadrat · Ostwert · Nordwert*:

```
32U NA 64846 21576     ← 10-digit, 1 m — GPSView's default (live GPS fix)
32U NA 648 215         ← 6-digit, 100 m — the taught voice/map default
```

- **Default precision: 10-digit (1 m)** — GPSView has a live GPS fix, not a hand-plotted map reading.
- Digit count is always even, split equally between easting and northing (4/6/8/10 → 1 km/100 m/10 m/1 m). No odd-digit forms exist.
- Conversion via `mil.nga:mgrs` (`MGRS.from(Point)`, `coordinate(GridType)`); **GPSView ships its own thin spacing formatter** — do not assume the library's `toString()` matches the BOS grouping (verify, §10).
- **Bavaria spans four grid zones — 32T, 32U, 33T, 33U** — around the 12°E/48°N crossing (near Regensburg/Cham). Not just 32U/33U. Tests must cover all four (§9).

### 6.2 Lat/lon

- **Display default: decimal degrees, 6 fractional places, German decimal comma** — `48,137154°`. **Toggle to DMS** available ([ticket 10 §1](wayfinder/tickets/10-grilling-residual-ux-decisions.md)).
- **Copy payload (decimal mode) deliberately deviates from display:** `48.137154, 11.575382` — **dot** decimals, `lat, lon`, no degree glyph — machine-pasteable into any maps search/CSV/dispatch tool. DMS mode copies the DMS string as displayed.

### 6.3 Plus Code

Full **global** code (no locality short form): `8FWH4HX8+9C`. Via `com.google.openlocationcode`, offline by construction.

### 6.4 Heights

Both heights always shown, clearly labeled, each with its own vertical-accuracy sub-line:

- **ellipsoidisch (WGS84)** — `getAltitude()`.
- **über NHN** (MSL/Geoid) — `getMslAltitudeMeters()`.

Copy payload per height row: bare value, e.g. `487 m` (the tapped row carries its own context). If a value is unavailable after enrichment, render a dashed row — never a stale or zero value.

### 6.5 Per-row copy payloads (summary)

Copy the displayed value verbatim, with one exception ([ticket 10 §2](wayfinder/tickets/10-grilling-residual-ux-decisions.md)):

| Row | Copies |
|---|---|
| UTMREF | `32U NA 64846 21576` (exactly as shown) |
| Lat/lon (decimal) | `48.137154, 11.575382` (dots — the one deviation) |
| Lat/lon (DMS) | the DMS string as displayed |
| Höhe rows | `487 m` |
| Plus Code | `8FWH4HX8+9C` |

### 6.6 Position-level Share payload

One German text block, UTMREF first, dotted decimals, capped with a tappable `https` Google Maps link (**not** `geo:` — `https` is tappable in every messenger; the Google dependency is acceptable for a personal share):

```
Standort (GPSView)
UTMREF: 32U NA 648 215
Koordinaten: 48.137154, 11.575382
Plus Code: 8FWH4HX8+9C
Höhe: 487 m (ellipsoidisch) · 453 m (NHN)
Genauigkeit: ±4 m · 11.07.2026 14:32
https://www.google.com/maps?q=48.137154,11.575382
```

Everything at once (both heights, accuracy, **timestamp** — keeps a stale share honest). Built by the pure `ShareFormatting` builder; UTMREF in the share uses the same precision as currently displayed.

---

## 7. Main screen — Layout A „Karte im Fokus“

Chosen from three prototyped variants ([prototype](wayfinder/tickets/05-prototype-main-screen.md); artifact: [main-screen-prototype.html](wayfinder/prototypes/main-screen-prototype.html)).

- **Map is the hero, full-bleed.** Top app bar: brand, a live GPS-fix **status chip**, and a single **Teilen** action (shares the whole position, §6.6). Standard map tools float on the map: zoom, locate/follow-me, layer toggle.
- **Bottom sheet, two zones:**
  - **Peek (always visible when collapsed):** the **primary UTMREF grid ref** (largest element, BOS format) **plus both heights** — *ellipsoidisch* and *über NHN* — as two equal, accent-bordered cards, each with its vertical-accuracy sub-line. Heights are glanceable without pulling the sheet up.
  - **Expanded (pull-up):** Breite/Länge (lat/lon, §6.2), Plus Code, then a metadata strip with horizontal accuracy (`±4 m`) and the satellite summary.
- **Satellite summary:** **verwendet / sichtbar ratio (`18 / 24`)** with a small fill bar. No per-constellation breakdown on this screen.
- **Per-row action = tap-to-copy** — the whole row is the tap target, confirmed by a toast. No per-row icon pairs; Share exists only once, in the app bar.
- **Typography:** coordinates in a monospaced, tabular-figures face so grid refs align. German labels, decimal commas in display.

### 7.1 Permission & error states (rendered within Layout A)

From [ticket 10 §5](wayfinder/tickets/10-grilling-residual-ux-decisions.md):

| State | Behavior |
|---|---|
| First launch (`NotYetAsked`) | Request the system permission **immediately, no pre-rationale card**. |
| Re-askable denial | Rationale card + retry button. |
| `PermanentlyDenied` | Screen with deep-link to app settings (`ACTION_APPLICATION_DETAILS_SETTINGS`). |
| Coarse-only grant („Ungefähr") | **Function but warn:** show what we can + persistent non-blocking banner „Genauer Standort empfohlen" with a re-request-precise button. Not a blocking wall. |
| `LocationOff` | Card + deep-link to `ACTION_LOCATION_SOURCE_SETTINGS` over a dimmed map. |
| `Acquiring` | Map visible, status chip „Kein Fix", bottom-sheet peek shows dashes + „Suche Satelliten…" with the `0/n` ratio. |

---

## 8. Map

### 8.1 Layers (one MapLibre style, three sources)

Declaration order = paint order; later layers paint on top.

**1. Base — OpenFreeMap vector tiles** (default visible). Public instance, no API key, no caps, self-hostable fallback. Use one of OpenFreeMap's published MapLibre style URLs (pick the most neutral available, e.g. their "Liberty"-class style — confirm the exact style URL from openfreemap.org at build time).

**2. Satellite — Bavarian DOP20 orthophotos** (opt-in toggle, default off). MapLibre `raster` source with a templated WMS `GetMap` URL against
`https://geoservices.bayern.de/od/wms/dop/v1/dop20`
(WMS 1.1.1/1.3.0, TLS 1.2, **no authentication**), using the `{bbox-epsg-3857}` placeholder. Bavaria-only coverage — acceptable by design.

**3. Parcel overlay — ALKIS-Parzellarkarte** (opt-in toggle, default off). MapLibre `raster` source ([research 08](wayfinder/research/08-cadastral-parcel-overlay.md)):

```
https://geoservices.bayern.de/od/wms/alkis/v1/parzellarkarte
  ?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap
  &LAYERS=by_alkis_parzellarkarte_umr_gelb&STYLES=Gelb
  &CRS=EPSG:3857&BBOX={bbox-epsg-3857}
  &WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE
```

- Outline-only layer, built by LDBV explicitly for overlay use; genuine alpha transparency confirmed by live test.
- **`STYLES` is mandatory** — requests without it are rejected (`MissingParameterValue`).
- Start with **yellow** (`umr_gelb`/`Gelb`) for legibility over both the vector base and the orthophoto; swap to `umr_schwarz`/`Schwarz` if visual testing says otherwise (§10).
- **`minzoom` ≈ 16–17** on this source/layer — don't request or render parcel lines before individual parcels are legible (provider optimizes for 1:1000; the 1:5000 max-scale is advisory, not server-enforced).
- Single Bavaria-wide endpoint; no district/tiling logic.

Each raster layer is toggled via the standard `visibility` layout property (`visible`/`none`) — independent, no style reload.

### 8.2 Interaction defaults

From [ticket 10 §6](wayfinder/tickets/10-grilling-residual-ux-decisions.md):

- **First-fix zoom: ~z16** (street level).
- **Follow-me: default ON**; drag disengages, locate button re-engages + recentres.
- **Rotation & tilt: locked, north-up.** Grid refs and parcels are read north-up.
- **Own position: blue-dot marker + translucent accuracy circle** sized to the horizontal accuracy (mirrors the `±4 m` readout). No heading cone.

### 8.3 Attribution overlay

A persistent small attribution corner (MapLibre's built-in attribution control where possible — OpenFreeMap's line is auto-added by it):

- Base layer visible → **"OpenFreeMap © OpenMapTiles Data from OpenStreetMap"** (includes the ODbL "© OpenStreetMap contributors" obligation).
- Satellite **or** parcel layer visible → **"Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"** (one line covers both Bavarian layers).

---

## 9. Testing requirements

**Pure JVM unit tests (plain JUnit, no Robolectric/device) on the `coordinates` package:**

- **Known-good fixture:** the EU geographic midpoint near Gadheim (`49.8431°N, 9.9019°E`) → `32U NA 648 215` at 6-digit precision (worked example from the official Merkblatt 9.008), plus its 10-digit form.
- **Zone-crossing coverage:** at least one fixture in each of Bavaria's four grid zones — **32T, 32U, 33T, 33U** — bracketing the 12°E/48°N crossing.
- Even-digit precision behavior at 4/6/8/10 digits; BOS spacing formatter output.
- Lat/lon formatters: display (comma) vs copy (dot) forms; DMS round-trip.
- Plus Code encoding fixture.
- `ShareFormatting`: every per-row copy payload of §6.5 and the full share block of §6.6 (fixed timestamp injected).

**ViewModel tests:** feed fake `LocationSource`/`GnssSource` flows through the constructor; assert the `combine` merge (fix without satellites → `Live` with null satellites; no fix → `Acquiring`; state transitions).

---

## 10. Verify empirically during the build

Open items the research flagged as unverifiable from documentation — each is a smoke test, none blocks starting:

1. **`mil.nga:mgrs` output spacing** — does `toString()`/`coordinate(GridType)` emit spaced or compact form? The own BOS formatter (§6.1) is required regardless; confirm what it wraps.
2. **`mil.nga:mgrs` toolchain smoke test** — last release April 2024; confirm it resolves/builds under the current AGP early.
3. **Does FLP populate `hasMslAltitude()` by itself?** If reliably yes, the §4.2 enrichment path becomes a rare fallback; code it anyway.
4. **Single GNSS chipset session** — with FLP high-accuracy + GnssStatus callback both registered, confirm one `onFirstFix` / no doubled power draw (Energy Profiler).
5. **`ON_STOP` teardown timing** — verify location updates stop instantly on backgrounding/task-switch on the target device ("stops instantly" is a hard requirement).
6. **`maplibre-compose` raster API surface** — confirm raster sources + per-layer visibility toggling are exposed in the Compose API; otherwise use the `AndroidView` fallback (§2).
7. **Parcel overlay color** — yellow vs black outlines over vector base and orthophoto; pick by eye.
8. **Geoid/MSL plausibility in Bavaria** — sanity-check the NHN value against a known local elevation.

---

## 11. About/Licenses screen („Über / Lizenzen")

A simple screen or dialog listing:

| Component / data | License | Note |
|---|---|---|
| MapLibre Native | BSD-2-Clause | |
| maplibre-compose | BSD-3-Clause | |
| `mil.nga:mgrs`, `mil.nga:grid` | MIT | NGA-authored |
| `com.google.openlocationcode` | Apache-2.0 | |
| OpenStreetMap data | ODbL | "© OpenStreetMap contributors" |
| OpenFreeMap tiles | free service | "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" |
| Bavarian DOP20 orthophotos | **CC BY 4.0** | "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de" |
| ALKIS-Parzellarkarte | **CC BY 4.0** — confirmed with LDBV Kundenservice ([ticket 09](wayfinder/tickets/09-task-confirm-parzellarkarte-license.md)); overrides the CC BY-ND note on the LDBV product page | same attribution line as DOP |

GPSView only displays unmodified WMS tiles; it never re-styles or re-derives the parcel geometry into a new dataset.
