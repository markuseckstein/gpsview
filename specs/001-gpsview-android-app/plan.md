# Implementation Plan: GPSView — Live Location Viewer

**Branch**: `001-gpsview-android-app` | **Date**: 2026-07-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-gpsview-android-app/spec.md`

**Governing detail source**: [SPEC.md](../../SPEC.md) (implementation-ready, decision-complete). This plan maps SPEC.md onto the speckit workflow; where this plan summarizes, SPEC.md's section references carry the full detail. Precedence: constitution → SPEC.md → wayfinder tickets/research → this plan.

## Summary

Build a single-module Kotlin/Compose Android app (min SDK 34, sideload-only) that shows the device's live position as a Bavarian-BOS UTMREF grid reference (hero value) plus lat/lon, Plus Code, ellipsoidal and NHN heights, accuracy, and satellite counts — and on a MapLibre map (OpenFreeMap base, optional Bavarian DOP20 orthophoto and ALKIS parcel overlays). Location flows are strictly foreground-only via lifecycle-scoped `callbackFlow` collection. All coordinate math lives in a pure, JVM-unit-tested `coordinates` package behind `LocationSource`/`GnssSource` interface seams.

## Technical Context

**Language/Version**: Kotlin (latest stable at build time), Jetpack Compose (BOM latest stable), JDK 17 toolchain, min SDK 34 / target SDK latest stable (Android 14+)

**Primary Dependencies** (SPEC.md §2 — version-pinned, license-recorded):
- `org.maplibre.gl:android-sdk` 13.3.1 (BSD-2) — map renderer
- `org.maplibre.compose` (maplibre-compose) v0.13.0 (BSD-3) — Compose wrapper; sanctioned fallback: classic `MapView` in `AndroidView`
- `mil.nga:mgrs` 2.1.3 + transitive `mil.nga:grid` 1.1.2 (MIT) — UTMREF/MGRS (plain-JVM artifact, **not** `mgrs-android`)
- `com.google.openlocationcode:openlocationcode` 1.0.4 (Apache-2.0) — Plus Codes
- `com.google.android.gms:play-services-location` latest stable — fused location
- AndroidX lifecycle ≥ 2.8.0 (`collectAsStateWithLifecycle`, `repeatOnLifecycle`)
- Deliberate exclusions: no Hilt/DI framework, no `core-location-altitude` compat (platform `AltitudeConverter` at API 34), no `mgrs-android`

**Storage**: none — no database, no persisted state in v1 (display preferences live in UI state; snapshot is never persisted)

**Testing**: plain JUnit on the JVM for `coordinates` (zero Android imports — no Robolectric, no device) and for the ViewModel (fake sources via constructor); on-device empirical smoke tests for SPEC.md §10 items

**Target Platform**: Android 14+ (API 34+), personal sideload APK — no Play Store constraints

**Project Type**: mobile app — single Gradle `:app` module, package-layered (`coordinates` / `data` / `ui`)

**Performance Goals**: live readout at ~1 s cadence (fix interval 1000 ms, no batching); first fix surfaced as soon as delivered; map camera follows at fix rate without jank

**Constraints**: strictly foreground-only (all registrations torn down on `ON_STOP`, no `ACCESS_BACKGROUND_LOCATION`, no foreground service); coordinate readouts fully offline-capable; German-only UI with decimal commas in display; battery-frugal (single GNSS session expected across FLP + GnssStatus)

**Scale/Scope**: single user, one Activity, one ViewModel, two screens (main + Über/Lizenzen), ~5 user stories / 24 functional requirements

## Constitution Check

*GATE: evaluated against constitution v1.0.0 — pre-Phase-0 and re-checked post-Phase-1.*

| # | Principle | Status | How the plan complies |
|---|---|---|---|
| I | Pure Coordinate Core (NON-NEGOTIABLE) | ✅ PASS | All conversion/formatting/share-payload code in `coordinates` package, zero Android imports, plain-JVM JUnit tests ([contracts/coordinates-api.md](contracts/coordinates-api.md)); `LocationSource`/`GnssSource` interfaces are the enforced boundary ([contracts/location-sources.md](contracts/location-sources.md)) |
| II | Strictly Foreground-Only Location (NON-NEGOTIABLE) | ✅ PASS | Both sources are cold `callbackFlow`s with `awaitClose` teardown, collected only via `collectAsStateWithLifecycle()` (STARTED-scoped); manifest declares only COARSE+FINE+INTERNET; no service, no batching (`setMaxUpdateDelayMillis(0)`) |
| III | On-Device Coordinate Truth | ✅ PASS | All formats derived on-device from the fix; MSL via platform offline `AltitudeConverter` (single app-lifetime instance, off-main-thread); unavailable fields → nullable snapshot fields → dashed rows, never fabricated |
| IV | German-First, BOS-Faithful Presentation | ✅ PASS | Own BOS spacing formatter (10-digit default, even digits only); German UI/decimal commas in display; documented copy-payload deviations (dot decimals) preserved exactly per SPEC.md §6 |
| V | Radical Simplicity & Deliberate Dependencies | ✅ PASS | One module, one Activity, one ViewModel, manual constructor injection via `ViewModelProvider.Factory`; every dependency pinned + license-recorded; leanest artifacts chosen (plain-JVM mgrs) |
| VI | Data Provenance & Attribution Compliance | ✅ PASS | Attribution per visible layer (OpenFreeMap/OSM line; Bayerische Vermessungsverwaltung line for DOP20/ALKIS); unmodified WMS tiles only; Über/Lizenzen screen lists all components ([contracts/map-services.md](contracts/map-services.md)) |

**Workflow gates**: test-first fixtures for the core mandated in [quickstart.md](quickstart.md) and contracts; SPEC.md §10 empirical items carried as explicit verification tasks (none blocks starting; each blocks calling its area done).

**Post-Phase-1 re-check (2026-07-11)**: design artifacts introduce no new dependencies, modules, or Android leakage into the core. ✅ PASS — no Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/001-gpsview-android-app/
├── plan.md              # This file
├── research.md          # Phase 0 output — consolidated wayfinder research decisions
├── data-model.md        # Phase 1 output — snapshot, UI states, derived representations
├── quickstart.md        # Phase 1 output — build, test, and on-device validation guide
├── contracts/           # Phase 1 output
│   ├── coordinates-api.md    # Pure-core API + golden fixtures
│   ├── location-sources.md   # LocationSource/GnssSource flow contracts
│   └── map-services.md       # External tile/WMS service contracts + attribution
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

Single Gradle `:app` module, package-layered (SPEC.md §3; a second module was considered and rejected — promoting a package later is cheap):

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml          # version catalog — all pins from SPEC.md §2
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml    # COARSE + FINE + INTERNET only
    │   └── kotlin/de/eckstein/gpsview/
    │       ├── coordinates/       # PURE — zero Android imports (the testability seam)
    │       │   ├── LatLon.kt, PositionSnapshot.kt
    │       │   ├── Utmref.kt      # mgrs wrapper + BOS spacing formatter
    │       │   ├── LatLonFormat.kt  # decimal/DMS display + copy forms
    │       │   ├── PlusCode.kt
    │       │   └── ShareFormatting.kt
    │       ├── data/              # Android-dependent, behind interfaces
    │       │   ├── LocationSource.kt   # interface + FLP callbackFlow impl (+ MSL enrichment)
    │       │   └── GnssSource.kt       # interface + GnssStatus callbackFlow impl
    │       └── ui/                # Compose + ViewModel (one Activity, one ViewModel)
    │           ├── MainActivity.kt
    │           ├── PositionViewModel.kt   # combine() → StateFlow<PositionUiState> + MapUiState
    │           ├── MainScreen.kt          # Layout A „Karte im Fokus": map + bottom sheet
    │           ├── MapContent.kt          # MapLibre style, 3 sources, marker, follow-me
    │           ├── PermissionStates.kt    # NotYetAsked/denied/LocationOff/coarse banner
    │           └── AboutScreen.kt         # Über / Lizenzen
    └── test/
        └── kotlin/de/eckstein/gpsview/
            ├── coordinates/       # plain JUnit: fixtures per contracts/coordinates-api.md
            └── ui/                # ViewModel tests with fake sources
```

**Structure Decision**: single `:app` module with the three-package layering above; file names within packages are indicative, the package boundaries are the enforced structure (constitution, SPEC.md §3). Application ID suggestion `de.eckstein.gpsview` — any personal reverse-domain works; fix it at project creation.

## Complexity Tracking

No constitution violations — table intentionally empty.
