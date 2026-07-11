<!--
Sync Impact Report
==================
Version change: (template, unversioned) → 1.0.0
Rationale: First concrete ratification of the GPSView constitution. All placeholder
tokens replaced with project-specific principles derived from SPEC.md and the
wayfinder decision record. MINOR/PATCH not applicable — this is the initial adoption
(1.0.0).

Principles (all newly defined):
  I.   Pure Coordinate Core (Testability Seam) — NON-NEGOTIABLE
  II.  Strictly Foreground-Only Location — NON-NEGOTIABLE
  III. On-Device Coordinate Truth
  IV.  German-First, BOS-Faithful Presentation
  V.   Radical Simplicity & Deliberate Dependencies
  VI.  Data Provenance & Attribution Compliance

Added sections:
  - Technology & Platform Constraints (was [SECTION_2_NAME])
  - Development Workflow & Quality Gates (was [SECTION_3_NAME])
  - Governance (filled)

Removed sections: none (template placeholders fully populated).

Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check gate reads this file
        dynamically; no hardcoded principle names to update. Verified aligned.
  ✅ .specify/templates/spec-template.md — no constitution references; no change needed.
  ✅ .specify/templates/tasks-template.md — no constitution references; no change needed.

Follow-up TODOs: none. Ratification date set to the constitution's authoring date
(2026-07-11), matching the SPEC.md assembly date.
-->

# GPSView Constitution

GPSView is a personal, sideload-only Android app that displays live details of the
device's current location — coordinates in multiple systems, GNSS metadata, and position
on a map with optional Bavarian imagery and cadastral overlays. This constitution encodes
the non-negotiable engineering principles that every feature, plan, and task MUST honor.
It derives from and is subordinate to the decisions recorded in
[SPEC.md](../../SPEC.md) and the wayfinder research/ticket trail.

## Core Principles

### I. Pure Coordinate Core (Testability Seam) — NON-NEGOTIABLE

All coordinate conversion, formatting, and share/copy-payload construction MUST live in
the `coordinates` package as pure `input → value`/`input → String` functions with **zero
Android imports**. This package MUST be unit-tested with plain JUnit on the JVM — no
Robolectric, no device, no emulator. The `LocationSource` and `GnssSource` interfaces are
the **enforced boundary** that keeps the Android platform out of the merge/derivation
logic; tests exercise the ViewModel and core by passing fakes through constructors.

**Rationale:** The correctness that matters most in GPSView — a fire-service grid
reference read aloud in the field — is deterministic math. Isolating it as pure code
makes it exhaustively testable without a device and prevents Android dependencies from
leaking into logic that must never depend on them. Any change that introduces an Android
import into `coordinates`, or that makes core logic untestable on the JVM, violates this
principle and MUST be rejected or refactored.

### II. Strictly Foreground-Only Location — NON-NEGOTIABLE

Location activity MUST occur only while the app is visible. Every location registration
(FusedLocationProviderClient and GnssStatus) MUST be torn down the instant the app is no
longer visible (`ON_STOP`), via lifecycle-scoped collection
(`collectAsStateWithLifecycle` / `repeatOnLifecycle(STARTED)`) and `callbackFlow`
`awaitClose` teardown. The app MUST NOT declare `ACCESS_BACKGROUND_LOCATION`, MUST NOT run
a foreground service for location, and MUST NOT use batching or any background-oriented
throttling technique. "Stops instantly on backgrounding" is a hard, verifiable
requirement.

**Rationale:** This is the app's core privacy and battery contract with its single user.
It is enforced structurally (by lifecycle, not by manual bookkeeping) so it cannot be
quietly eroded. Any feature requiring background location is out of scope by definition.

### III. On-Device Coordinate Truth

Every coordinate readout (lat/lon, UTMREF/MGRS, Plus Code) and every height value MUST be
computed on-device from the GNSS fix, with no network dependency. When connectivity is
absent, all coordinate readouts MUST keep working; only the online basemap may degrade
(blank/last-rendered tiles). MSL/geoid height MUST use the platform's bundled, offline
`AltitudeConverter`. Unavailable fields render as an explicit dashed row — **never** a
stale, zero, or fabricated value.

**Rationale:** A location tool is worthless the moment it depends on the network to tell
you where you are. Offline-resilient readouts and honest absence-of-data are what make
GPSView trustworthy in the field.

### IV. German-First, BOS-Faithful Presentation

All user-facing text MUST be German. Displayed numbers MUST use the German decimal comma.
UTMREF/MGRS is the hero format and MUST follow the Bavarian BOS convention (space-grouped
Zonenfeld · 100-km-Quadrat · Ostwert · Nordwert), default 10-digit (1 m) precision, via
GPSView's own spacing formatter. **Copy/share payloads deliberately deviate from display**
where machine-pasteability requires it (dot decimals, `lat, lon` order, no degree glyph) —
these deviations are specified, intentional, and MUST be preserved exactly as documented
in SPEC.md §6.

**Rationale:** GPSView speaks the language and conventions of Bavarian fire services; a
grid reference that doesn't match the taught Merkblatt format is a bug. Simultaneously,
copied coordinates must paste cleanly into maps and dispatch tools — so display fidelity
and copy fidelity are distinct, deliberate contracts, not an inconsistency to "fix."

### V. Radical Simplicity & Deliberate Dependencies

The app is one Gradle `:app` module, one Activity, one ViewModel, wired by **manual
constructor injection** — no DI framework. Every dependency MUST be justified, version-
pinned, and license-recorded (SPEC.md §2, §11). Prefer the leanest artifact that does the
job (e.g. plain-JVM `mil.nga:mgrs` over the Android-tile variant). New dependencies,
modules, or abstraction layers MUST be justified against YAGNI; "we might need it later"
is not justification when promoting a package to a module later is cheap.

**Rationale:** A personal, single-purpose app earns its reliability through smallness.
Ceremony (DI graphs, speculative modularity, redundant libraries) adds surface area and
build fragility with no user-visible benefit.

### VI. Data Provenance & Attribution Compliance

Every external data source's license obligations MUST be honored at runtime and recorded.
Attribution MUST be shown whenever the corresponding layer is visible (OpenStreetMap/ODbL
and OpenFreeMap for the base; "Bayerische Vermessungsverwaltung – www.geodaten.bayern.de"
for DOP20 and ALKIS layers). GPSView MUST only display unmodified provider tiles — it MUST
NOT re-style, re-derive, or republish parcel geometry or imagery into a new dataset. An
About/Licenses screen MUST list every component and data source with its license.

**Rationale:** The Bavarian cadastral and orthophoto data are used under CC BY 4.0;
correct attribution and non-derivation are licensing obligations, not optional polish.
Getting provenance right keeps the app legitimate to use and share.

## Technology & Platform Constraints

- **Stack:** Kotlin + Jetpack Compose. **min SDK 34** (Android 14+). Google Play services
  permitted. Platform APIs available at SDK 34 (e.g. `AltitudeConverter`) MUST be used
  directly rather than pulling in compatibility shims.
- **Distribution:** personal sideload only — no Play Store, no F-Droid. Design decisions
  MUST NOT be constrained by store-distribution requirements, and MUST NOT assume them.
- **Architecture:** package-layered single module — `coordinates` (pure) / `data`
  (Android-dependent sources behind interfaces) / `ui` (Compose + ViewModel). The layer
  boundaries in SPEC.md §3 are the enforced structure.
- **Scope discipline:** background location, track recording, navigation/routing, POI
  search, offline tile caching, what3words, and labeled Flurstück numbers are explicitly
  out of scope for v1. Deferred items (e.g. offline caching) MUST remain cleanly additive,
  not designed out.

## Development Workflow & Quality Gates

- **Test-first for the core:** the `coordinates` package MUST ship with JVM unit tests
  covering the mandated fixtures — the Gadheim known-good reference, all four Bavarian grid
  zones (32T, 32U, 33T, 33U), even-digit precision (4/6/8/10), lat/lon display-vs-copy
  forms, DMS round-trip, Plus Code encoding, and every `ShareFormatting` payload
  (SPEC.md §9). ViewModel merge behavior MUST be tested with fake sources.
- **Empirical smoke tests:** items that documentation cannot settle (SPEC.md §10) MUST be
  verified on-device during the build — including instant `ON_STOP` teardown, single GNSS
  chipset session, MGRS spacing output, MSL plausibility, and the maplibre-compose raster
  API surface. None blocks starting; each blocks calling the relevant area done.
- **Traceability:** every plan and non-trivial change SHOULD trace to a SPEC.md section or
  wayfinder ticket. Deviations from SPEC.md MUST be recorded as an explicit, reasoned
  decision, not a silent drift.
- **Constitution Check gate:** plans MUST pass the Constitution Check (plan template)
  against these principles before implementation; violations MUST be justified in the
  Complexity Tracking section or the plan MUST be revised.

## Governance

This constitution supersedes ad-hoc practice for GPSView. When guidance conflicts, the
order of precedence is: this constitution → [SPEC.md](../../SPEC.md) → wayfinder
tickets/research → individual plans.

- **Amendments** MUST be made by editing this file with an updated Sync Impact Report and
  a version bump, and MUST propagate any consequent changes to dependent templates and
  docs in the same change.
- **Versioning policy** (semantic): **MAJOR** for backward-incompatible principle removals
  or redefinitions; **MINOR** for a new principle/section or materially expanded guidance;
  **PATCH** for clarifications and non-semantic refinements.
- **Compliance review:** every plan runs the Constitution Check gate; every review MUST
  verify that changes respect the two NON-NEGOTIABLE principles (I and II) in particular.
  Complexity and new dependencies MUST be justified against Principle V.

**Version**: 1.0.0 | **Ratified**: 2026-07-11 | **Last Amended**: 2026-07-11
