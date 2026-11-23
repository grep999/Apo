# ADR-022 — Five Playable Hues

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 1–3, 10, 13, 19.

## Decision

Retain all five playable hues—Ember, Gold, Moss, Tide and Violet—through M0 and launch. Do not remove a hue to simplify onboarding. Teach progressively through glyphs, staged enemy/card introduction and the first-run ramp.

## Rationale and baseline adherence

Five hues define the Spectrum wheel and the deck-paints-board differentiator. The v0.2 spec explicitly uses five; onboarding should solve complexity rather than deleting identity.

## Acceptance tests

All five hues appear in the wheel and board generator; each has unique glyph; staged tutorial introduces them; grayscale/color-vision tests show no hue depends on color alone.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
