# ADR-008 — Wall And Overflow

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 2–3; existing implementation DECISION_LOG.md and ADR-008 tests.

## Decision

Use row 0 as the enemy/top edge and row 6 as the player/bottom edge; the hidden preview row sits below row 6. A normal rise shifts unsealed columns toward row 0, promotes the incoming row to row 6, then generates a new incoming row. If the top row cannot accept the rise, Overflow deals 8 unmitigated Vigil damage, burns the top two rows in unsealed columns, consumes one Flask, skips the shift and preserves the pending incoming row. Sealed columns are excluded from the rise and remain fixed per ADR-012.

## Rationale and baseline adherence

The 8 damage, two-row burn and skipped rise are explicit in v0.2. The one-Flask penalty is carried forward from the existing tested engine decision log and is made explicit here rather than being silently imported. The pending row is retained so Overflow does not erase the next pressure event.

## Acceptance tests

Golden tests for normal rise, top-row occupancy, sealed-column exclusion, multiple occupied cells, 8 damage unaffected by Block, one Flask consumed, top-two-row burn, skipped shift, pending-row retention and deterministic save/replay.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
