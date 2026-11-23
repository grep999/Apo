# EFB-005 — Class Identity and Starter Decks

**Status:** IMPLEMENTED — exact-SHA CI PASS  
**Branch:** `feat/efb005-class-starters`  
**Base:** `feat/efb004-card-engine` at `f699767c54dd86373855935b36d5e7adeb2cac04`

## Delivered

- Three typed class identities: Ashen Monk (Ember/Moss), Quarry Witch (Tide/Violet), and The Unlit (Gold/Ash).
- Exact 12-card starter deck for each class, with stable card IDs and deterministic compatibility with `CardBattleState.start`.
- The Unlit deck preserves ADR-014's exact multiset: 4 Gold Strike, 4 Ash Ward, 2 Pop Shot, 1 Sift, 1 Hollow Crown card. The Hollow Crown card and `relic_hollow_crown` remain distinct IDs and entities.
- Signature relic contracts are data-defined: Prayer Beads (+2 Ink on first Tap), Chisel Wand (one free recolor per fight), and Hollow Crown (+1 Spark per Ash block popped).
- Implemented deterministic Sift: shuffles movable board and incoming-row slots using a run/turn/card-derived RNG stream, preserves sealed columns and Spectrum state, then draws one card.
- Added the Monk's Sacrifice Rite as a transactional Vigil-risk card: lose 6 Vigil, deal 12 damage and draw 2; it cannot reduce the player to zero Vigil.
- Quarry Witch's Prism Seed creates a Prism adjacent to a selected anchor and draws one card.
- Unlit Ash Ward creates Ash, grants 3 Block and 1 Spark.

## Scope boundary

Signature relic rules are represented as validated content contracts in this increment; the relic trigger runtime is not yet connected to combat. The next increment wires relic triggers into the reducer and adds end-to-end combat tests. No Android UI or save serialization is claimed.

## Verification

`.github/workflows/efb005-class-starters.yml` runs the complete Kotlin/JVM test suite and pure-JVM boundary check against the exact pushed SHA.


**Verified candidate:** `9afb2fb4cb2dca5ff7f5d59ffc64a6454508584a`  
**GitHub Actions run:** [45292799130](https://github.com/grep999/Apo/actions/runs/45292799130) — full Kotlin/JVM test suite PASS; pure-JVM boundary PASS.
