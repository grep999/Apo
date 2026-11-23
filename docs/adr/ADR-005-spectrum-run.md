# ADR-005 — Spectrum Run

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Section 3.

## Decision

At turn start, `run=0` and `lastRunHue=null`. The first pop establishes the anchor and pays at ×1.0. Each subsequent pop of the next hue on Ember→Gold→Moss→Tide→Violet→Ember increments Run by one and uses `1 + 0.5×run`. An out-of-order or repeated hue pays at ×1.0 and becomes the new anchor with Run reset to zero. Reset at Cleanup. A Prism counts as the next expected wheel hue for one step only; multiple Prisms do not grant multiple steps.

## Rationale and baseline adherence

The spec defines the wheel and multiplier but leaves anchor/reset edge cases implicit. This is the smallest deterministic interpretation and preserves the first pop at base payout.

## Acceptance tests

Table-driven tests for first pop, ordered chain, wrap, repeated/out-of-order hue, reset, Prism substitution, rounding once at final payout.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
