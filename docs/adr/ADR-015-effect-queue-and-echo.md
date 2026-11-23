# ADR-015 — Effect Queue And Echo

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 4, 14.

## Decision

Resolve effects through one deterministic FIFO queue with monotonic sequence IDs. Order: initiating action → authored primary effects → board mutations → Pop/Stone/Brand triggers → relic triggers → enemy reactions → cleanup. Children append in authored order. Echo repeats only the last top-level card cast this turn, once; it cannot repeat Echo, an already echoed card, or recursively replay nested triggers. Cap each action at 128 queued events and depth 16. Exceeding either limit rolls back the initiating action transactionally and emits a deterministic diagnostic.

## Rationale and baseline adherence

The v0.2 spec requires data-driven effects, deterministic replay and a narrow Echo. Bounded queue/depth limits prevent runaway content combinations while preserving atomicity.

## Acceptance tests

Golden nested trigger ordering; Echo scope and one-repeat cap; FIFO order; 128-event and depth-16 boundaries; overflow rollback leaves committed state unchanged; same seed/input log yields identical event sequence.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
