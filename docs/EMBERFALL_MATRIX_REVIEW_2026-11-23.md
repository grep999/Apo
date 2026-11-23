# EMBERFALL — Multi-Policy Matrix Result Review

**Candidate:** `911e2272da14371c1fc271ead406c8bcaa04b145`  
**Workflow:** https://github.com/grep999/Apo/actions/runs/45147504116  
**Artifact:** `emberfall-matched-seed-policy-matrix.zip` (SHA-256 `9e31894e7b09a86f4b41eb6233707ae8c1c5c36b0f630e9b9dec5b84d6751f17`)  
**Review date:** 2026-11-23

## Verified result

The uploaded CSV has 45 class/policy rows: 15 route/combat combinations × 3 classes. Every row contains 20 runs. Across 900 simulated campaigns:
- 900 wins;
- 0 incomplete runs;
- 0 recorded losses in the artifact;
- all policies average 12 unlocked passives, 3 filled support sockets and 8 owned artifacts;
- average level range: 12.95–13.85;
- average deck size range: 18.05–18.75.

The Deck Curator results are numerically identical to Balanced results for all three combat policies and all three classes.

## Review verdict

**CONDITIONAL — acceptable as a matrix plumbing/smoke increment; not acceptable as balance evidence.**

Reasons:
1. 20 seeds per class/cell is too small for balance conclusions.
2. 100% bot win rate does not meet the specification's 15–30% Ascension-0 target, but the bots are not calibrated human proxies and the sample is limited.
3. Full progression saturation in every policy cell means the simulator does not yet expose reward scarcity.
4. Deck Curator has not demonstrated distinct behavior in this sample.

## Required next iteration

- Instrument selected node types and merchant visits by policy.
- Record reward offer, pick, skip and reroll counts; include selected card/relic/support/artifact IDs.
- Record damage taken, HP/Flask use, enemy turns, Wall overflow and defeat causes.
- Add a lookahead combat policy; retain matched seeds across policies.
- Run at least 1,000 seeds per class/cell before evaluating balance changes.
- Do not change XP thresholds, item effects, Cinders or reward cadence until the stronger evidence is available.

## P0 regression found in Android UI suite

The Android verification run failed one Robolectric assertion: the test expected the inline next-room button while support and artifact rewards were still unresolved. The UI correctly gates advancement on `RewardBundle.allChoicesResolved`; the test expectation was stale. The test is updated to assert both reward channels are visible and next-room is absent until both are resolved, then assert navigation becomes available.

## Additional engine guard

The support-selection command now rejects an unknown catalog ID or a support already installed in the character's sockets. This closes the case where the helper no-ops but the command handler could still mark the reward channel as picked.
