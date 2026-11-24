# EFB-006 — Signature Relic Runtime

**Status:** IMPLEMENTED — exact-SHA CI PASS  
**Branch:** `feat/efb006-signature-relic-runtime`  
**Base:** `feat/efb005-class-starters`

## Delivered

- Class battle initialization equips the selected class's signature relic by stable ID.
- Prayer Beads: the first successful manual Tap each turn grants +2 Ink of the resolved hue, with overflow converted to Spark. The trigger resets at the next Draw phase.
- Chisel Wand: one free recolor of an unsealed Hue block per fight; the action preserves the block's Brand marker and cannot be reused.
- Hollow Crown: every Ash cell in a popped group grants +1 Spark, including card-driven Pops.
- CardBattleState now persists equipped relic IDs, once-per-fight usage and per-turn Prayer Beads usage in immutable snapshots.
- Added explicit card-engine commands for manual Tap and signature relic recolor so UI input can use the same reducer boundary as card actions.
- Golden tests cover first-Tap-only behavior, per-turn reset, one-use recolor and Ash Spark payout through card Pop.

## Verification

`.github/workflows/efb006-signature-relics.yml` runs the complete Kotlin/JVM test suite and pure-JVM boundary check on the exact pushed SHA.

## Scope boundary

This implements the three class signature relics only. General relic inventory, arbitrary trigger ordering, relic stacking, save migration and relic UI remain future increments.


**Verified candidate:** `ea2b723363cd888c8dd69961958d206c99337391`  
**GitHub Actions run:** [45293276435](https://github.com/grep999/Apo/actions/runs/45293276435) — full Kotlin/JVM suite PASS; pure-JVM boundary PASS.
