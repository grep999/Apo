# ADR-004 — Prism Resolution

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Section 3.

## Decision

A normal hue Tap flood-fills orthogonally connected cells of that hue plus adjacent Prism cells. Prism does not bridge two different base-hue components into one mixed-hue group. A direct Prism Tap resolves the largest adjacent playable-hue/Ash component; ties prefer the next expected Spectrum hue, then fixed wheel order. A Prism may join an Ash group, but Ash still pays zero Ink. A Prism-only cluster without an adjacent eligible group is not legal.

## Rationale and baseline adherence

This gives each Prism one deterministic group membership and preserves its wildcard value without allowing mixed-hue ambiguity or order-dependent resolution.

## Acceptance tests

Golden boards for one Prism/one hue, one Prism/two hues, multiple Prisms, direct Prism Tap, Prism+Ash, ties and no-adjacent-hue; replay hashes match.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
