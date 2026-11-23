# EMBERFALL — P0 CI and Support Reward Guard Follow-up

Date: 2026-11-23

## Grounding

- Repository: `grep999/Apo`
- Base branch: `implementation/emberfall-latest-source`
- Base SHA: `e3610946ecf4caf91f0e1d2a232af714b88e65c2`
- Existing reward-resolution commits are already present on the base branch; this increment does not duplicate them.
- Latest Android Verification run inspected: https://github.com/grep999/Apo/actions/runs/45145053793

## Findings

1. **BUILD BLOCKER:** `CombatArenaScreen.kt` declared a default callback of type `(String, Int) -> Unit` with a zero-argument lambda.
2. **BUILD BLOCKER:** `EncounterScreens.kt` referenced undefined `relicResolved` when deciding whether the Skip Card label may say “& Map”.
3. **P0 correctness gap:** the support command guard checked that an ID was offered and that replacement capacity was valid, but did not reject an unknown catalog ID or an already socketed support. The progression helper returns the original state for those inputs, while the command handler could still mark the reward as picked.

## Changes in this candidate

- Corrected the no-op callback default to `{ _, _ -> }`.
- Added a derived `otherChoicesResolved` predicate for the Skip Card label. The “& Map” wording now appears only after relic, support and artifact channels are resolved.
- Hardened support selection validation against unknown and duplicate support IDs.
- Added regression coverage asserting both invalid selections are rejected without changing the RunState.
- Extended the engine contract and decision log.

## Required gates

- Android unit + Robolectric tests.
- Debug and release lint.
- Debug APK assembly.
- Release APK and AAB assembly.
- Independent Maestro Mastery review on the exact candidate SHA.

## Evidence classification

- **VERIFIED:** the two Kotlin errors were reproduced in the latest GitHub Actions log; exact source locations inspected.
- **IMPLEMENTED:** candidate patch is prepared on an isolated branch.
- **PLANNED:** Android CI and independent Mastery must run against the pushed candidate.
- **NOT CLAIMED:** no build/test pass, merge, release, or device acceptance is claimed by this report.
