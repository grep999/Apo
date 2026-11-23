# EFB-002 Board Core — Implementation Status

**Status:** IMPLEMENTATION CANDIDATE — NOT ACCEPTED  
**Base:** `f9a41aa373d36d41a0c134c8005fb5984fecd2b3`  
**Branch:** `feat/efb-002-board-core-victory`  
**PR:** #9 (draft)

## Delivered

- Pure Kotlin/JVM board module; no Android or Compose imports.
- Immutable 7×7 state, separate incoming row and sealed-column mask.
- Deterministic SplitMix64 RNG and seed-derived independent board/enemy/card streams.
- Deck-frame hue weighting, 35-cell opening topology, 32 full-board retries and explicit fallback telemetry.
- Orthogonal Tap/Pop; Prism direct selection tie-break; no cross-hue bridging.
- Ash zero-Ink pop, n−1 chip output, Brand per-cell cost.
- Stone snapshot break, vertical-only gravity, frozen Seal columns.
- Spectrum Run payout using integer round-half-up.
- Wall rise and Overflow transitions.
- Command replay and canonical SHA-256 board checkpoint digest.
- Unit/golden tests and a 100,000-seed statistical gate.

## Local verification performed

Environment: OpenJDK 21.0.12.1, Kotlin compiler 1.9.0.

1. Main Kotlin sources compiled with `kotlinc`: PASS.
2. Deterministic generation smoke over seeds 0–99,999: PASS.
3. Sampled cells: 4,200,000 (35 visible + 7 incoming per seed).
4. Legal opening group: present in all 100,000 generated boards.
5. Generation fallback: 0 / 100,000 (0.000%).
6. Observed versus target hue shares:
   - Ember: 33.8278% vs 33.8600%
   - Gold: 7.6562% vs 7.6749%
   - Moss: 22.5829% vs 22.5733%
   - Tide: 28.2521% vs 28.2166%
   - Violet: 7.6810% vs 7.6752%

The local smoke and statistical harnesses were run directly with the Kotlin compiler/runtime. They are not represented as Gradle/JUnit CI evidence.

## Known gaps before acceptance

1. Gradle/JUnit workflow must pass on the exact PR candidate.
2. Independent Mastery review must inspect the exact candidate and test artifacts.
3. The EFB-000 merged baseline still needs the independent review evidence called out by the handoff governance.
4. ADR-002 mentions neutral-block rules but the accepted ADR does not quantify initial neutral spawn probabilities. This implementation does not invent probabilities; initial random generation samples playable hues only. A quantified neutral generation rule needs an accepted ADR before implementation.
5. Direct-Prism tie-break, Prism+Ash, mixed sealed-column Wall rise, no-Flask Overflow, replay digest golden vectors and extreme Ink groups need additional golden/property coverage.
6. The game does not yet have EFB-003 combat, EFB-004 card effects, Android UI, saves, content, audio or release validation.

## Acceptance position

This branch is a concrete EFB-002 start, not a complete EFB-002 milestone and not a playable game release. Do not merge until the exact candidate passes all gates and independent review.
