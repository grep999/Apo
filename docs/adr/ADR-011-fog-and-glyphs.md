# ADR-011 — Fog And Glyphs

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 5, 10, 13; existing ADR-011 decision log/tests.

## Decision

Fog lasts for three player Tap actions. It conceals true hue tint and Ink pool values, but never removes the unique high-contrast hue glyph, group boundaries, focus or selection cues. Gold Eye immediately restores hue/Ink visibility and reveals the next two Wall rows while active.

## Rationale and baseline adherence

This retains the established readable-Fog behavior while preserving the v0.2 accessibility rule that glyphs remain readable. Fog obscures information, not the ability to identify or target a group.

## Acceptance tests

Countdown on exactly three Tap actions; glyph remains distinguishable at 44dp; TalkBack labels/focus survive; Gold Eye clears immediately; reduced-motion/high-contrast tests.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
