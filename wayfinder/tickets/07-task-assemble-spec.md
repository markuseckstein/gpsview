---
name: Assemble the implementation-ready spec
labels: [wayfinder:task]
status: closed
assignee: markus
blocked-by:
  - 05-prototype-main-screen.md
  - 06-grilling-architecture.md
  - 09-task-confirm-parzellarkarte-license.md
  - 10-grilling-residual-ux-decisions.md
---

## Question

Assemble every decision on this map into the single implementation-ready spec document — the destination of this effort.

To resolve (AFK task): write `SPEC.md` in the repo, consolidating: stack and constraints (map Notes), library and API mandates (research tickets 01–04), the cadastral parcel overlay (ticket 08, license text confirmed by ticket 09), screen layout (prototype ticket 05), architecture (ticket 06), and whatever the fog patches ("Not yet specified") graduated into. The spec must be complete enough that a single build effort needs no further decisions.

Deliverable: `SPEC.md`, linked from the resolution.

## Resolution

Assembled 2026-07-11 (AFK). Deliverable: [SPEC.md](../../SPEC.md) at the repo root.

The spec consolidates, with per-section links back to its sources:

- **Constraints & non-goals** — map Notes + Out of scope (incl. the offline-caching deferral and the what3words/Flurstücksnummer exclusions).
- **Dependencies** — exact artifacts/versions from research 01/04 (`org.maplibre.gl:android-sdk` + maplibre-compose, `mil.nga:mgrs`+`grid`, `openlocationcode`), with the deliberate exclusions (`mgrs-android`, `AltitudeConverterCompat`, DI framework) stated.
- **Data layer** — FLP request parameters, in-flow MSL enrichment via platform `AltitudeConverter`, separate `GnssStatus` source, `collectAsStateWithLifecycle` teardown, manifest permissions (research 02/03).
- **Architecture** — single `:app` module, pure `coordinates` package seam, raw-truth snapshot, sealed `PositionUiState` + separate `MapUiState`, manual constructor injection (ticket 06).
- **Coordinate formats** — BOS UTMREF spacing/10-digit default and four-zone caveat (research 04), decimal/DMS lat/lon, per-row copy payloads and the German share block verbatim (ticket 10).
- **UI** — Layout A „Karte im Fokus" with peek/expanded sheet contents and all permission/error states (tickets 05, 10).
- **Map** — three-source MapLibre style with full WMS URL templates for DOP and Parzellarkarte, interaction defaults, attribution rules (research 01/08, ticket 10).
- **Parzellarkarte license** — recorded as **CC BY 4.0** per the LDBV confirmation in ticket 09, superseding research 08's conservative CC BY-ND assumption.
- **Testing requirements** and the **eight empirical smoke-test items** the research flagged as unverifiable from docs.

Nothing was invented: every decision in the spec traces to a closed ticket or research file. The map's destination is reached — no open tickets remain.
