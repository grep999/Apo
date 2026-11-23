# EFB-003 — Turn / Combat Core

**Status:** IMPLEMENTATION CANDIDATE — exact-SHA CI and independent Mastery pending  
**Candidate SHA:** `8dd0a03ceae047a72d783eeee81df037952ac18c`  
**Baseline:** accepted EFB-002 board core `6b6e139890a205ead337a94226464a0b5ddd82da`  
**Branch:** `feat/efb003-turn-combat-clean`  
**Policy:** clean-room implementation; legacy prototype source/assets explicitly excluded by `engineering/PRODUCT_OWNER_DECISION_CLEAN_CORE.md`.

## Implemented in this candidate

- Explicit Draw → Weave → Cast → Wall → Enemy → Cleanup lifecycle, with terminal Victory/Defeat.
- Atomic command/reducer boundary; invalid commands return a rejection without mutating the input state.
- Three base Taps; accepted board actions consume one Tap, rejected actions do not.
- Reuses EFB-002 `BoardEngine` for Tap/Pop, Spectrum, Wall/Overflow, Seal countdown and board digest; the combat adapter preserves sealed incoming-row cells because the lower-level Wall API replaces the incoming row globally.
- Player Vigil, Block, Flasks, capped per-hue Ink and Spark overflow.
- Enemy Vigor/Block, two-intent deterministic forecast and exact forecast/resolution identity.
- Strike, Ash Row, Stone Seed, Stain, Fog, Seal, Brand, Surge, Snatch and Curse intent contracts; board-targeting enemy intents respect sealed columns.
- Card integration seam for Cast damage/Block/delay and Card Pop without spending a Tap; Snatch and Curse emit explicit EFB-004 integration events rather than inventing a card model early.
- Versioned command replay frames and deterministic combat checkpoint digest including board, resources, full enemy definition/intent sequence/cursor and status counters.

## Scope intentionally excluded

Card definitions/effect DSL, deck/draw/discard implementation, class-specific content, enemy catalogue/boss phases, Android UI, art/audio, run progression, saves across app restarts and backend.

## Verification

The new exact-SHA workflow runs the full Gradle/JUnit suite and rejects Android/Compose imports in the pure domain source. An earlier exact-SHA run compiled production and test Kotlin, passed the pure-JVM boundary, and found one stale Seal/Stain incoming-row assertion (58 tests, 1 failure). That assertion and the sealed incoming-row integration were corrected. Earlier candidate runs exposed and fixed two issues: one stale Seal/Stain assertion and one misplaced test-class closing brace. Final candidate run 45273205685 is pending; no PASS is claimed. The exact candidate SHA is recorded above. Device/UI testing is not applicable to this headless increment.

## Remaining review

Independent Mastery must inspect the exact final candidate SHA, all changed paths, raw CI evidence, transactional invalid-action behavior, intent preview parity, status durations, and replay digest stability. Then record Repetition.