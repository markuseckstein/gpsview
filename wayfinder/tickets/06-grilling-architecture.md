---
name: Settle app architecture and module structure
labels: [wayfinder:grilling]
status: open
assignee:
blocked-by:
  - 01-research-map-library-and-imagery.md
  - 02-research-gnss-metadata-and-height.md
  - 03-research-location-provider-and-battery.md
  - 04-research-coordinate-conversion-libraries.md
---

## Question

Given the chosen libraries and APIs, what is the app's architecture — state model, layering, and testability seams?

To resolve (HITL, `/grilling` + `/domain-modeling`):

- Domain model for a "position snapshot" (fix + derived coordinate representations + GNSS metadata) and its update flow into Compose state.
- Single-module vs light layering; where conversion logic lives so it's unit-testable offline.
- Error/absence states as domain concepts (no permission, GPS off, no fix yet, MSL unavailable).

Deliverable: resolution comment capturing the agreed architecture; feeds directly into the spec.
