# EFB-004 — Card / Effect Engine

**Status:** IMPLEMENTED — exact-SHA CI running  
**Branch:** `feat/efb004-card-engine`  
**PR:** https://github.com/grep999/Apo/pull/16  
**Base:** `feat/efb003-turn-combat-clean` at `b6267fadf573f12247592a20ad0657b15a016dee`

## Delivered

- Typed card definitions with stable IDs, frame hue, type, rarity, colored/generic costs, base/upgraded effect payloads, Exhaust and Retain.
- Immutable card-zone snapshots for draw pile, hand, discard and exhaust.
- Deterministic seeded shuffle and five-card draw at turn start.
- Explicit payment preview/validation. Colored pips require matching Ink; generic pips consume Spark first, then least-abundant Ink with Spectrum wheel tie-break.
- Deterministic FIFO effect queue with monotonic sequence IDs, 128-event action budget, depth-16 limit and transactional rollback on effect failure.
- Echo repeats the previous top-level non-Echo card once; Echo Chime exhausts.
- Combat integration: damage, Block, Pop by target or largest legal group, enemy-intent delay, draw and next-turn Tap bonus.
- Board integration: 3×3 recolor, adjacent spawn, row/column shatter with gravity and two-group Fuse. Board mutations preserve sealed columns.
- Cleanup routes unplayed Retain cards to the next hand and other unplayed cards to discard.
- Ten original vertical-slice card definitions.

## Tests

Nineteen focused JUnit tests cover catalog shape, deterministic draw, colored/generic payment, Spark rules, unaffordable atomicity, damage, largest-group Pop, Echo/exhaust, Echo rollback, queue and depth limits, sealed recolor, shatter/gravity, next-turn Tap bonus and Retain cleanup.

## Known scope limits

- Pure Kotlin/JVM only; no Android screen/device integration yet.
- This is a small vertical-slice catalogue, not the full 360-card target.
- Card upgrade selection, relic trigger graph, class-specific starter decks, localization and save serialization remain subsequent increments.
- EFB-003 source audit findings remain documented on PR #15; this increment does not claim they are fixed.

## Verification

Workflow: `.github/workflows/efb004-card-engine.yml`  
Command: `gradle --no-daemon clean test`  
Pure JVM boundary: rejects Android/Compose imports from domain source.  
Candidate SHA is the current branch head; update this record when the exact-SHA workflow finishes.
