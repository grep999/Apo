# ADR-016 — Shade Inheritance

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 1, 7.

## Decision

A Shade appears only on the next run after a death, at Act 1 row 5. It is generated from the immediately previous dead deck snapshot as a bounded strategy/intention profile; it does not execute unrestricted player input. Its next two intents are visible. Victory returns exactly the Cinders carried at death and offers one Remnant selected from up to three distinct eligible cards in the dead deck. No relics, passive progression or other permanent power is inherited. Skipping permanently fades that Shade and forfeits that recovery reward; Shades do not stack.

## Rationale and baseline adherence

This implements the spec's bounded death-as-content promise without turning it into a meta-currency or compounding power system.

## Acceptance tests

No Shade after win; exactly one after death; row 5; deterministic profile; exact Cinders recovery; Remnant eligibility; skip forfeiture; no relic/passive inheritance; two-intent visibility.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
