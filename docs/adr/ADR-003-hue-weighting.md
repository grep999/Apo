# ADR-003 — Hue Weighting

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 3–4.

## Decision

For each playable hue h, compute `rawWeight(h) = max(0.08, (hueCardCount(h)+1)/(deckSize+5))`, then normalize the five playable-hue weights. A card contributes by its explicit playable `frameHue`, not by the semantic effect it performs. Dread contributes Violet. An Ash-themed card with a Gold frame contributes Gold. Neutral board tiles (Ash, Stone, Prism) do not create a sixth hue or add to any hue numerator.

## Rationale and baseline adherence

This follows the explicit formula and the rule that a card's frame hue paints the board. It resolves the Ash/Gold edge case without conflating board-tile kind with card hue.

## Acceptance tests

Golden 5 Ember / 4 Tide / 3 Moss example; Dread contributes Violet; Gold-framed Ash Ward contributes Gold; five weights normalize to 1; floor is applied before normalization; sampling confidence tests.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
