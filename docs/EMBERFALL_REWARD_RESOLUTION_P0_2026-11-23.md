# Emberfall — P0 Reward Resolution and Persistence

**Target source:** `implementation/emberfall-latest-source`  
**Follow-up candidate:** `feat/emberfall-multi-policy-balance-lab`  
**Date:** 2026-11-23

## Defect

The reward bundle contained four independent channels: card, relic, Dhāraṇā support and Tantric artifact. The standalone Victory Spoils screen exposed only card/relic, while the inline overlay exposed all four. The engine's FinishRewards guard also checked only card/relic. A player could therefore leave the standalone screen with unresolved support/artifact choices, silently discarding them.

Support socketing also had an implicit fallback that replaced the last socket when the set was full and no replacement index was supplied.

The simulation runner selected the first event option without checking its resource requirements. A rejected choice could be followed by a rejected LeaveNode command, producing an incomplete run.

## Changes

- Added computed `RewardBundle.allChoicesResolved`; it includes card, relic, support and artifact channels without changing serialized schema.
- FinishRewards now rejects any unresolved reward channel.
- Support replacement requires an explicit valid slot when sockets are full; invalid or implicit replacement is rejected transactionally.
- Inline spoils and standalone RewardsScreen now use the same completion contract.
- Standalone RewardsScreen now exposes support and artifact choices, including skip actions.
- Full support sets show a slot-selection dialog rather than silently replacing the last support.
- ViewModel reward callbacks use explicit replacement indices and persist all reward-channel actions.
- Accepted reward actions enqueue a FIFO flush barrier after their save/finish request. The existing worker remains FIFO, uses NonCancellable around Room writes, and preserves sticky persistence failures.
- Added Room save/reload coverage for a snapshot with unresolved support and artifact choices.
- Simulator event selection now chooses only currently affordable options and checks command acceptance before leaving the event.

## Verification plan

GitHub Actions workflow: `.github/workflows/android-verification.yml`

Expected checks:
1. Pure-JVM engine boundary.
2. Full JVM + Room migration + audio DSP + Robolectric Compose test task.
3. Debug and release lint.
4. Debug APK assembly.
5. Release APK and AAB assembly with an ephemeral CI-only key.
6. Test report and package artifacts.

The workflow run is in progress; no pass is claimed until its conclusion and artifacts are inspected.

## Remaining caveat

The FIFO queue and flush barrier reduce the process-death window for reward choices, but cannot guarantee persistence if Android kills the process before the asynchronous flush completes. The existing process-recreation test explicitly awaits the flush barrier before constructing a new ViewModel. The Room snapshot regression now includes unresolved reward channels. A hard kill at arbitrary instruction boundaries remains an OS-level test, not a claim established by Robolectric.

## No balance tuning

No XP thresholds, passive-point schedules, socket milestones, item effects, Cinders values, reward cadence or deck-size policy were changed. Balance tuning remains gated on the multi-policy matrix and larger matched-seed runs.
