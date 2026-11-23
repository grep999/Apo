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
