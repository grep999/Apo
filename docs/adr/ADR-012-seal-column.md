# ADR-012 — Seal Column

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Section 5; existing implementation ENGINE_CONTRACT.md, DECISION_LOG.md and ADR-012 tests.

## Decision

Seal locks a column for three complete player turns. While sealed, player Taps, group membership, gravity/refill, Chisel targeting, player card paint/shatter/recolor/fuse effects and Wall-row insertion cannot affect cells in that column. Enemy-originated effects may apply only if their own definition explicitly overrides Seal. The seal marker is attached to the column coordinate and decrements after each enemy phase; blocks remain fixed relative to that column while unsealed columns move.

## Rationale and baseline adherence

The v0.2 spec establishes a three-turn column lock. The existing engine contract and regression tests already define the stronger frozen-column behavior; retain it rather than reopening the decision or replacing it with a weaker proposal. This makes the exception explicit and testable.

## Acceptance tests

Tap, Pop, gravity, refill, Chisel, paint, shatter, recolor, fuse, Wall rise and enemy Stain tests; exact three-turn countdown; sealed/unsealed columns remain rectangular and deterministic.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
