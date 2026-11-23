# ADR-013 — Enemy Vigor And Block

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 5, 12.

## Decision

For fight index n (zero-based number of prior fights in the current act), base Vigor is `roundHalfUp(36×1.11^n)`. Apply ×1.6 for elite and ×3.0 for boss, then round once half-up. Enemy Block absorbs all player Pop/card damage through the current player action phase and clears at the start of the next enemy phase. Block granted by an enemy intent protects the next player phase. Player Block clears at the start of the next player turn.

## Rationale and baseline adherence

This follows the v0.2 scaling formula and makes the indexing, rounding and Block lifetime deterministic. Integer state avoids platform drift.

## Acceptance tests

Golden values around rounding boundaries, act reset, elite/boss multipliers, enemy Block gain/absorb/expiry, player Block reset and replay parity.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
