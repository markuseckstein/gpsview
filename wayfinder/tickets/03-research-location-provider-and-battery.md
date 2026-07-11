---
name: Research location provider strategy and battery behavior
labels: [wayfinder:research]
status: open
assignee:
blocked-by: [02-research-gnss-metadata-and-height.md]
---

## Question

What location-request strategy gives fluid live updates in the foreground with minimal battery cost, and how is "stops instantly when backgrounded" implemented cleanly?

To resolve (AFK research, `/research`) — builds on the GNSS-metadata findings (fused vs `GPS_PROVIDER` may already be constrained by what satellite/height data requires):

- Fused location provider vs raw `LocationManager.GPS_PROVIDER` for a live foreground display: fix quality, warm-up time, battery cost, and whether both must run simultaneously (fused for position, GnssStatus for satellites).
- Recommended request parameters (interval, priority) for a "live but frugal" screen-on use case; whether adaptive throttling is worth speccing.
- Lifecycle pattern in Compose (e.g. `repeatOnLifecycle(STARTED)`) guaranteeing no background access — and what manifest permissions to request (`ACCESS_FINE_LOCATION` only, explicitly no background permission).

Deliverable: markdown summary linked here, with the concrete strategy the spec should mandate.
