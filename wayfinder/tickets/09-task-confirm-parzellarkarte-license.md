---
name: Confirm the ALKIS-Parzellarkarte's actual license with LDBV
labels: [wayfinder:task]
status: closed
assignee: Markus
blocked-by: []
---

## Question

[Research Bavarian cadastral parcel overlay integration](08-research-cadastral-parcel-overlay.md) found a genuine conflict between primary sources on the ALKIS-Parzellarkarte's license: the WMS `GetCapabilities` and OpenData JSON say **CC BY 4.0**, but the dedicated LDBV product page (https://www.ldbv.bayern.de/produkte/liegenschaftsinformationen/parzellarkarte.html) explicitly singles this product out as **CC BY-ND 4.0**. This doesn't block building the overlay (GPSView only displays unmodified tiles, "sharing" under either variant), but the spec's About/Licenses screen needs the correct text.

To resolve (HITL task — the agent cannot authoritatively settle a live legal/licensing discrepancy on its own):

- Email `service@geodaten.bayern.de` (the Kundenservice contact listed in the WMS `GetCapabilities` document) asking which license actually governs the ALKIS-Parzellarkarte WMS: CC BY 4.0 or CC BY-ND 4.0.
- Record the answer here on resolution — it decides the exact license line the spec's About/Licenses screen must show for this layer.

Deliverable: the confirmed license, recorded in the resolution comment.

## Resolution

CC BY 4.0