# ADR-010 — Brand Cost

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 5, 13; existing ADR-010 decision log/tests.

## Decision

Each branded block actually included in a Pop costs 2 Vigil. The cost is per block, not per group. If Vigil is insufficient, the shortfall reduces HP. Only effects whose semantics explicitly perform a Pop trigger Brand; recolor, fuse, gravity, Wall movement and non-Pop destruction do not. Preview exact cost before commitment.

## Rationale and baseline adherence

The spec says marked blocks cost 2 Vigil when popped. The existing decision log and tests establish per-block cost and shortfall handling; this ADR makes the scope explicit.

## Acceptance tests

One/multiple branded blocks, mixed groups, manual Tap, Pop card, recolor, fuse, row destruction and Brand Breaker; preview equals actual Vigil/HP loss.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
