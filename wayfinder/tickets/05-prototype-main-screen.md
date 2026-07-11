---
name: Prototype the main screen layout
labels: [wayfinder:prototype]
status: open
assignee:
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
