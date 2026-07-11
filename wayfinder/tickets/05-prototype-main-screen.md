---
name: Prototype the main screen layout
labels: [wayfinder:prototype]
status: closed
assignee: claude
blocked-by: []
---

## Question

How should the single main screen be laid out — the information hierarchy between the map, the coordinate formats (lat/lon, UTMREF, Plus Code), and the GNSS metadata (accuracy, satellites, both heights)?

To resolve (HITL, `/prototype`): build a cheap throwaway mock (e.g. HTML or Compose stub with fake data, German labels) and iterate with Markus. Key sub-questions to settle by reacting to the artifact:

- Map-dominant with a details sheet, or details-dominant with a map card?
- How prominently the two height values appear (they're flagged important).
- Where tap-to-copy and share affordances live per coordinate row.
- How satellite count is summarized (used/visible? per constellation?).

Deliverable: the prototype linked here plus the agreed layout description.

## Resolution

Prototype (three variants, then folded to the winner): [wayfinder/prototypes/main-screen-prototype.html](../prototypes/main-screen-prototype.html) · viewable Artifact: https://claude.ai/code/artifact/08787d50-d04c-4760-b738-ee23516cdd86

Markus reacted to three structurally distinct layouts (A map-dominant / B details-dominant / C split-dashboard). **Chosen: Layout A — „Karte im Fokus“.** Agreed design the spec should mandate:

- **Map is the hero**, full-bleed. A top app bar carries the brand, a live GPS-fix status chip, and a single **Teilen (Share)** action that shares the whole position. Standard map tools (zoom, follow-me/locate, layer toggle) float on the map.
- **Bottom sheet, two zones:**
  - **Peek (always visible when collapsed):** the **primary UTMREF/MGRS** grid ref (largest element, BOS format) **plus both heights** — ellipsoidisch (WGS84) and über NHN (MSL/Geoid) — as two equal, accent-bordered cards, each with its own vertical-accuracy sub-line. This satisfies the "heights are important" flag: they're glanceable without pulling the sheet up.
  - **Expanded (on pull-up):** Breite/Länge (lat/lon), Plus Code, then a metadata strip with horizontal accuracy and the satellite summary.
- **Per-row action = tap-to-copy** (whole coordinate row is the tap target, confirmed by a toast). No per-row copy/share icon pair. **Share is a single position-level action** in the app bar (not per row).
- **Satellite summary:** simple **verwendet / sichtbar ratio (`18 / 24`)** with a small fill bar. The per-constellation breakdown (GPS/GLO/GAL/BDS) shown in variant B was **not** chosen for the main screen — if wanted, it belongs on a details/expanded view, not the primary readout.
- **German UI throughout; decimal commas** (`48,137154°`); coordinates in a monospaced, tabular-figures face so grid refs align.

Decisions deferred (still fog, now sharper — feed the spec / architecture ticket): exact lat/lon variant (decimal vs DMS) and the copy/share **payload** text formats; the precise share sheet contents; permission/GPS-off/no-fix empty states within this layout.
