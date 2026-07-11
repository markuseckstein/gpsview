---
name: Settle residual UX and product decisions
labels: [wayfinder:grilling]
status: closed
assignee: claude
blocked-by:
  - 05-prototype-main-screen.md
  - 06-grilling-architecture.md
---

## Question

A handful of genuine product/UX decisions remain undecided after the architecture is settled. They are small and independent, but each is a real choice an AFK spec-assembler must not invent — they belong in a HITL pass before the spec is written.

To resolve (HITL, `/grilling`, possibly `/prototype` for the screens):

- **Lat/lon display variant** — decimal degrees vs DMS (and what each copy/share payload emits). Screen placement and UTMREF precision/grouping are already decided ([prototype 05](05-prototype-main-screen.md), [research 04](../research/04-coordinate-conversion-libraries.md)); only the string bodies of the already-placed pure `ShareFormatting` functions remain.
- **Offline tile caching** — build local tile caching for field use with no signal, or defer it. Licensing permits it (OpenFreeMap + Bavarian DOP both CC BY 4.0); it is a pure product decision. If deferred, say so explicitly in the spec.
- **Permission & error-state screens** — what the `NotYetAsked` / `PermanentlyDenied` / `LocationOff` / `Acquiring` states (the sealed `PositionUiState` cases from [architecture 06](06-grilling-architecture.md)) actually show and how the first-run flow behaves (rationale prompt, Settings deep-link copy), rendered within Layout A.
- **Map interaction defaults** — default zoom, rotate/tilt enabled or not, follow-me default on/off, marker + accuracy-circle rendering. The `MapUiState` shape is decided ([architecture 06](06-grilling-architecture.md)); only the concrete default values and gesture set remain.

Deliverable: resolution comment fixing each of the above; feeds directly into the spec. Blocks *Assemble the implementation-ready spec*.

## Resolution

Settled via `/grilling` (HITL, 2026-07-11). All four residual areas decided; nothing here needs further input before the spec.

### 1. Lat/lon display variant

Default **decimal degrees**, 6 fractional places (~11 cm, finer than any accuracy shown), German decimal comma, with a **decimal ⇄ DMS toggle**. The toggle is a display preference in UI state (alongside the map toggles), not a field on the snapshot. Lat/lon stays the *secondary* readout — UTMREF is the BOS hero, Plus Code covers casual sharing — so a single default variant plus an opt-in DMS view is enough.

### 2. Per-row copy payloads (tap-to-copy)

Copy the **displayed value verbatim, with one deliberate exception for pasteability:**

- **UTMREF row →** `32U NA 648 215` (exactly as shown; BOS reads it back).
- **Lat/lon row, decimal mode →** `48.137154, 11.575382` — **dot** decimals, `lat, lon`, no degree glyph, so it pastes straight into any maps search / CSV / dispatch tool. (DMS mode copies the DMS string as displayed.)
- **Height rows →** bare `487 m` (the tapped row already carries its own context).
- **Plus Code row →** the full global code, `8FWH4HX8+9C`.

Principle: hero formats copy WYSIWYG because a human reads/types them; only **decimal lat/lon** deviates from the German comma, because its whole value is machine-pasted. These are the bodies of the pure `ShareFormatting` functions placed by [architecture 06](06-grilling-architecture.md).

### 3. Position-level Share („Teilen") payload

One German text block sharing the whole position, UTMREF first, dotted decimals, capped with a tappable link:

```
Standort (GPSView)
UTMREF: 32U NA 648 215
Koordinaten: 48.137154, 11.575382
Plus Code: 8FWH4HX8+9C
Höhe: 487 m (ellipsoidisch) · 453 m (NHN)
Genauigkeit: ±4 m · 11.07.2026 14:32
https://www.google.com/maps?q=48.137154,11.575382
```

- **Everything at once** (both heights, accuracy, **timestamp**): a share says "here's where I am"; the recipient may want any format, and the timestamp keeps a stale share honest.
- **Link is an `https` Google Maps URL, not `geo:`** — it's read on someone else's device, and `https` is tappable in every messenger/platform while `geo:` often renders as dead text. The Google dependency in the link is acceptable for a personal share.

### 4. Offline tile caching — deferred from v1

**v1 is online-basemap-only.** Offline shows a blank/last-tiles map while **all coordinate readouts keep working** (they're computed on-device from the GNSS fix — no network). Rationale: the safety-critical data survives offline regardless; real offline-region management (download UI, storage accounting, eviction) is a disproportionate build for a "map picture visible in a dead zone" benefit; and it's cleanly additive later with no rework against the chosen architecture. Recorded on the map's **Out of scope** as deferred (not forbidden). The spec must state the online-only behavior explicitly.

### 5. Permission & error-state flow

- **First launch: request the system permission immediately, no pre-rationale card** — the app's purpose (a map named GPSView) is self-evident, so an Android rationale screen is boilerplate here.
- **Re-askable denial →** rationale card + retry button. **Permanent denial →** `PermanentlyDenied` screen with a deep-link to app settings (`ACTION_APPLICATION_DETAILS_SETTINGS`).
- **Coarse-only grant (Android 12+ „Ungefähr") → function but warn loudly:** show what we can, with a persistent non-blocking banner „Genauer Standort empfohlen" + a button to re-request precise. Not a blocking wall.
- **Mechanical per-state renders (within Layout A):** `LocationOff` → card + deep-link to `ACTION_LOCATION_SOURCE_SETTINGS` over a dimmed map; `Acquiring` → map visible, status chip „Kein Fix", bottom-sheet peek showing dashes + „Suche Satelliten…" with the `0/n` ratio.

### 6. Map interaction defaults

- **First-fix zoom:** ~z16 (street level).
- **Follow-me: default ON.** Dragging the map disengages it; the locate button re-engages and recentres.
- **Rotation & tilt: locked, north-up.** Grid references and the parcel overlay are read north-up; free rotation adds disorientation for no BOS benefit.
- **Default base layer: vector basemap (OpenFreeMap);** satellite (Bavarian DOP) is the opt-in toggle; parcel overlay **off** by default.
- **Own position:** blue-dot marker + translucent **accuracy circle** sized to horizontal accuracy (mirrors the `±4 m` readout). No heading cone in v1.

This clears the last fog. Only *Assemble the implementation-ready spec* remains.
