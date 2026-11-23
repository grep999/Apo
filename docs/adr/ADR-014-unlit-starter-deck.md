# ADR-014 — Unlit Starter Deck

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 3–4, 6.

## Decision

The Unlit starts with exactly 12 cards: 4 Gold Strike, 4 Ash Ward (Gold frame; Ash-themed effect), 2 Pop Shot, 1 Sift and 1 Ash Alchemy signature card. Hollow Crown is the class signature relic, not a thirteenth/overloaded starter-deck card. Ash Ward's Gold frame contributes to Gold spawn weight; its Ash behavior does not create a sixth playable hue. Dread is a Violet curse and contributes to Violet weight.

## Rationale and baseline adherence

This reconciles the 12-card starter recipe with the Gold/Ash identity and distinguishes the signature card from the signature relic. It corrects the older log's conflation of Hollow Crown (relic) with a starter card.

## Acceptance tests

Deck count exactly 12; exact card ID multiset; Gold frame metadata on Ash Ward; Ash Ward effect remains Ash; Hollow Crown equipped as relic; Dread counts Violet; no sixth hue.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
