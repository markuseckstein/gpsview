# Quickstart & Validation Guide: GPSView

How to build, test, and empirically validate the feature end-to-end. Details live in [plan.md](plan.md), [data-model.md](data-model.md), and [contracts/](contracts/); this guide only tells you how to prove things work.

## Prerequisites

- JDK 17
- Android SDK with platform 34+ (Android Studio or command-line tools); `ANDROID_HOME` set
- A physical Android 14+ device with GNSS, USB-debugging enabled (emulators can't validate real fixes, satellite counts, or battery behavior — JVM tests don't need any device)
- Network access for Gradle and, on-device, for map tiles

## Build & install

```bash
./gradlew assembleDebug                          # build APK
./gradlew installDebug                           # sideload to connected device
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Early sanity gate (empirical check E2): the very first successful `assembleDebug` proves `mil.nga:mgrs` 2.1.3 resolves and builds under the current AGP.

## JVM test suite (no device, no Robolectric)

```bash
./gradlew testDebugUnitTest
```

Must cover (binding fixtures in [contracts/coordinates-api.md](contracts/coordinates-api.md)):

- Gadheim known-good UTMREF (`32U NA 648 215` @ 6-digit) + 10-digit form
- One fixture per Bavarian grid zone: 32T, 32U, 33T, 33U
- Even-digit precision behavior (4/6/8/10) + BOS spacing formatter
- Lat/lon display (comma) vs copy (dot) forms; DMS round-trip
- Plus Code fixture (`8FWH4HX8+9C`)
- Every per-row copy payload + full share block (fixed timestamp injected)
- ViewModel merge with fake sources ([contracts/location-sources.md](contracts/location-sources.md)): fix-without-satellites → `Live(satellites=null)`; no-fix → `Acquiring`; state transitions per [data-model.md](data-model.md)

## On-device validation scenarios

Map to spec success criteria (SC) and SPEC.md §10 empirical checks (E).

### V1 — First fix & live readout (SC-001, SC-002; US1)

Outdoors, permission granted, open the app → grid reference within 30 s; all rows populated or dashed (never zero/stale); readouts update ≤ every 2 s while walking. Cross-check the UTMREF against an independent converter; sanity-check NHN height against a known local elevation (E8).

### V2 — Foreground-only teardown (SC-004; E5, E4) — hard requirement

1. With live updates running, press home / switch task / lock screen.
2. Verify via Android Studio Energy Profiler + logcat that location and GNSS registrations stop within 1 s.
3. While foregrounded, confirm a single GNSS session (one `onFirstFix`, no doubled power draw) with FLP + GnssStatus both registered (E4).

### V3 — Offline coordinate truth (SC-006)

Get a fix → enable airplane mode with location on → all coordinate readouts keep updating from the fix; map tiles degrade to blank/last-rendered only.

### V4 — Copy & share fidelity (SC-005, SC-007; US3)

Tap each row once → paste elsewhere → matches the contract payloads (decimal lat/lon pastes with dots into a maps search and resolves to the same spot). Share (≤2 taps) → German block per contract; the `https` link opens the position.

### V5 — Map behavior (SC-008; US2; E6)

First fix centres at ~z16 with follow-me on; drag disengages; locate button recentres + re-engages; rotation/tilt impossible; accuracy circle size corresponds to the `±n m` readout. E6 gate: raster sources + visibility toggles work through maplibre-compose — else switch to the `AndroidView` fallback.

### V6 — Bavarian overlays & attribution (SC-008, SC-009; US4; E7)

At a known Bavarian location: toggle DOP20 → imagery appears; toggle parcels → outlines align with base-map geometry, absent when zoomed out beyond legibility, yellow legible over both base and orthophoto (E7 — else switch to black). Attribution lines correct per [contracts/map-services.md](contracts/map-services.md) for every layer combination. Outside Bavaria: layers show nothing, map keeps working.

### V7 — Permission & system states (US5)

Walk the matrix via device settings: fresh install → immediate system prompt; deny once → rationale card + retry; deny permanently → settings deep-link; coarse-only → functioning app + persistent „Genauer Standort empfohlen" banner with re-request; location off → card + deep-link over dimmed map; before first fix → „Suche Satelliten…" with dashes + `0/n`.

### V8 — Über / Lizenzen (SC-009)

Screen lists every component and data source with license and attribution wording per [contracts/map-services.md](contracts/map-services.md).

## Definition of validated

All JVM tests green **and** V1–V8 pass on the physical target device. V2 is the constitution's NON-NEGOTIABLE foreground-only gate — a failure there blocks release regardless of everything else.
