# EFB-003 — Turn / Combat Core

**Status:** IMPLEMENTATION CANDIDATE — exact-SHA CI and independent Mastery pending  
**Baseline:** accepted EFB-002 board core `6b6e139890a205ead337a94226464a0b5ddd82da`  
**Branch:** `feat/efb003-turn-combat-clean`  
**Policy:** clean-room implementation; legacy prototype source/assets explicitly excluded by `engineering/PRODUCT_OWNER_DECISION_CLEAN_CORE.md`.

## Implemented in this candidate

- Explicit Draw → Weave → Cast → Wall → Enemy → Cleanup lifecycle, with terminal Victory/Defeat.
- Atomic command/reducer boundary; invalid commands return a rejection without mutating the input state.
- Three base Taps; accepted board actions consume one Tap, rejected actions do not.
- Reuses EFB-002 `BoardEngine` for Tap/Pop, Spectrum, Wall/Overflow, Seal countdown and board digest.
- Player Vigil, Block, Flasks, capped per-hue Ink and Spark overflow.
- Enemy Vigor/Block, two-intent deterministic forecast and exact forecast/resolution identity.
- Strike, Ash Row, Stone Seed, Stain, Fog, Seal, Brand, Surge, Snatch and Curse intent contracts.
- Card integration seam for Cast damage/Block/delay; Snatch and Curse emit explicit EFB-004 integration events rather than inventing a card model early.
- Deterministic combat checkpoint digest including board, resources, intent sequence/cursor and status counters.

## Scope intentionally excluded

Card definitions/effect DSL, deck/draw/discard implementation, class-specific content, enemy catalogue/boss phases, Android UI, art/audio, run progression, saves across app restarts and backend.

## Verification

The new exact-SHA workflow runs the full Gradle/JUnit suite and rejects Android/Compose imports in the pure domain source. No PASS is claimed until the workflow completes against the final candidate SHA. Device/UI testing is not applicable to this headless increment.

## Remaining review

Independent Mastery must inspect the exact final candidate SHA, all changed paths, raw CI evidence, transactional invalid-action behavior, intent preview parity, status durations, and replay digest stability. Then record Repetition.