# Emberfall — Matched-Seed Balance Matrix

**Branch:** `feat/emberfall-multi-policy-balance-lab`  
**Date:** 2026-11-23  
**Status:** implementation candidate; CI verification pending

## Purpose

The prior simulator used one route preference, one reward-selection rule and one combat heuristic. Its outcomes therefore described one deterministic bot, not the robustness of the campaign's progression economy.

This change adds a matched-seed matrix without changing production balance values.

## Matrix

Five route/reward policies:

| Policy | Intended pressure |
|---|---|
| Balanced | Existing default route and reward selection |
| Survival | Campfires, defensive cards and conservative elite avoidance |
| Elite hunter | Elite/Shade exposure, damage-oriented reward picks |
| Treasure seeker | Treasure/event preference and alternate artifact choice |
| Deck curator | Merchant preference and non-starter reward preference |

Three combat policies:

| Policy | Flask threshold | Defense posture | Swap/card posture |
|---|---:|---|---|
| Defensive | 56% HP | Treat incoming damage above current guard as urgent | Favor ink generation, guard, flask gain and Wall control |
| Balanced | 42% HP | Preserve the existing +6 guard buffer | Balanced damage/guard heuristic |
| Aggressive | 28% HP | Tolerate more incoming damage before prioritizing defense | Favor preview damage and damage/prism card effects |

The cross-product is 5 × 3 = **15 policy cells**. Every cell uses the same seed label for each class/index pair. Default run size is 100 seeds per class per cell (1,500 campaign simulations); the test suite uses one seed per class per cell (45 runs).

## Recorded metrics

Each policy cell reports terminal accounting and per-class:
- wins, HP defeats, zero-Flask collapses and incomplete runs;
- turn-cap and node-cap hits;
- average turns and Act reached;
- average level and XP remainder;
- average unspent passive points and passives unlocked;
- average support sockets and artifacts owned;
- average Cinders, deck size, HP and Flasks.

These are simulator outcomes, not player win rates or fun measurements. Policy diversity improves coverage but does not replace human playtesting.

## Running

From the Android/JVM project environment:

```kotlin
val report = SimulationRunner.runPolicyMatrix(seedsPerClass = 100, dreadLevel = 0)
```

Use 100 seeds/class for initial calibration. Increase to 1,000 matched seeds/class before accepting balance changes. Keep the same seeds, class order and policy definitions when comparing candidate economy changes.

## Guardrails

- No XP threshold, passive point, artifact, support, Cinders, card, relic or enemy balance values are modified by this change.
- No candidate balance adjustment is approved by the matrix alone.
- A policy cell with incomplete runs must not be counted as wins or losses.
- The event simulator now chooses only affordable options and checks command acceptance before leaving an event.
- Full Android build, Robolectric UI, lint and package verification are delegated to the GitHub Actions workflow.


## First matrix result review — 20 matched seeds per class/cell

The successful matrix workflow artifact for candidate `911e2272da14371c1fc271ead406c8bcaa04b145` contains 45 class/cell rows (15 policy cells × 3 classes), each with 20 runs: **900 simulated campaigns total**.

Observed in the artifact:
- All 900 runs completed and won; every row reports 20 wins and 0 incomplete runs.
- Every row averages 12/12 passives, 3/3 support sockets and 8/8 artifacts.
- Average level ranges from 12.95 to 13.85; average deck size is approximately 18.05–18.75.
- The Deck Curator rows are numerically identical to Balanced rows for all three combat policies and all three classes.

Interpretation:
- This is a successful **matrix smoke/coverage result**, not evidence of player balance or fun.
- The 100% win rate is far above the specification's 15–30% Ascension-0 target, but the current bots are simple deterministic heuristics and the sample is only 20 seeds per class/cell. Do not nerf production values from this result.
- Full progression saturation across every policy confirms the current policy matrix still does not stress reward scarcity. It does not establish that human runs receive every reward.
- Deck Curator is not empirically differentiated in this sample. Add route-node counts, merchant visits, reward offer/pick/skip counts and chosen card IDs before treating it as an independent policy.
- Next balance gate: increase to at least 1,000 matched seeds per class/cell, instrument policy actions and defeat causes, and add stronger/lookahead combat policies. Keep production tuning blocked until the data shows a stable signal.
