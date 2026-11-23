# EFB-002 Board Core — Implementation Status

**Status:** IMPLEMENTATION CANDIDATE — P1 PAYOUT FIX APPLIED; INDEPENDENT MASTERY PENDING  
**Base:** `handoff/emberfall-coding-agent-v1.0` (current base SHA is recorded in PR #14 metadata)  
**Branch:** `feat/efb002-remediation-rebased`  
**PR:** #14 (draft)  
**Original remediation commit:** `6932b747d78f9310ecb859cc9079cb2fc28ac9bf` (preserved as the second parent of the integration candidate)

## Delivered

- Pure Kotlin/JVM board module; no Android or Compose imports.
- Immutable 7×7 state, separate incoming row and sealed-column mask.
- Deterministic SplitMix64 RNG and seed-derived independent board/enemy/card streams.
- Deck-frame hue weighting, 35-cell opening topology, 32 full-board retries and explicit fallback telemetry.
- Orthogonal Tap/Pop; deterministic direct-Prism selection; no cross-hue bridging.
- Ash zero-Ink pop, n−1 chip output, Brand per-cell cost.
- Stone snapshot break, vertical-only gravity, frozen Seal columns and three-enemy-phase expiry.
- Spectrum Run payout using integer round-half-up arithmetic.
- Wall rise and Overflow transitions.
- Command replay and canonical SHA-256 board checkpoint digest.
- Unit, regression, randomized-property and 100,000-seed distribution/fallback tests.

## Remediation record

The original rejected candidate was `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`. The bounded remediation commit `6932b747d78f9310ecb859cc9079cb2fc28ac9bf` fixes:

1. `BoardState.fromRows` now accepts explicit Seal expiry timers and supplies the three-turn default.
2. `SpectrumState` rejects positive runs without an anchor hue.
3. Spectrum run progression and Ink payout arithmetic avoid Int overflow.
4. `BoardReplay.replay` returns an unmodifiable result collection.

The integration candidate preserves that exact remediation commit in its ancestry and retains the newer base-side Maestro workflow so PR-triggered checks execute.

## Verification evidence

- Project-owner local report on remediation SHA `6932b747...`: 37 tests and Gradle checks passed. Local evidence remains owner-reported.
- Prior Copilot CLI re-review: ACCEPT on remediation SHA `6932b747...`, as reported by the project owner; it does not cover subsequent payout correction.
- Current exact integration head `c420506334488bf9b2ff39316342a50a07bf6c85`:
  - Board-core Gradle/JUnit: PASS, run [45264659205](https://github.com/grep999/Apo/actions/runs/45264659205).
  - Maestro GateRunner exact-SHA checks: PASS, run [45264659293](https://github.com/grep999/Apo/actions/runs/45264659293).
  - EFB-001 ADR validator: PASS, run [45264659152](https://github.com/grep999/Apo/actions/runs/45264659152).
  - GateRunner bound both required checks to the exact SHA with exit code 0, runner identity `github-actions/45264659293` and log hashes.
- Payout fix: half-Ink is represented as integer half-units through Spectrum multiplication; only final payout rounds half-up. Regression tests cover 9/11/13-cell groups and Run 0/1.
- Hue-weight tests now assert independently calculated golden normalized weights and a floor-dominated profile, rather than only comparing sampling to the same implementation under test.
- Exact-head checks do not constitute independent Mastery review.

## Known gaps before acceptance

1. Fresh exact-SHA GitHub checks must pass on the latest PR #14 head.
2. Independent Mastery review must inspect the latest exact candidate, code, tests, workflow and artifacts. The current Maestro assignment is only `PROPOSED`; no reviewer has been assigned or dispatched.
3. EFB-000 merged-tree independent review remains a separate prerequisite.
4. ADR-002 does not quantify neutral initial spawn probabilities. The implementation does not invent probabilities; initial random generation samples playable hues only. A quantified neutral-generation rule requires an accepted ADR before implementation.
5. Maestro state continuity across workflow runs is not yet solved; the current Actions workflow creates a new workflow/database for each run. Its artifact is evidence for that run, not proof of durable cross-run workflow resumption.
6. The project is not yet a playable game. EFB-003 combat, EFB-004 card effects, Android UI, saves, production content, audio, balance and release validation remain future work.

## Acceptance position

EFB-002 is an implemented pure-JVM board-core candidate, not a complete game milestone and not a playable release. Keep PR #14 in draft. Do not merge until the latest exact-SHA CI, independent Mastery review, EFB-000 merged-tree review and required Maestro governance gates are satisfied.