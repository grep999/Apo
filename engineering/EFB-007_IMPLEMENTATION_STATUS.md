# EFB-007 — First-Slice Encounters

**Status:** IMPLEMENTED — exact-SHA CI PASS  
**Branch:** `feat/efb007-enemy-encounters`  
**Base:** `feat/efb006-signature-relic-runtime`

## Delivered

- Five original Act I encounter definitions: Glassling tutorial, Cinderling, Glass Moth, Quarry Husk and The Hollow Warden.
- Each encounter owns a typed `EnemyDefinition` with deterministic looping intent sequence and two-intent forecast.
- Glassling is explicitly board-only: no Strike intents, cards disabled and Ink disabled. Its teaching contract covers group pops, chip damage, vertical gravity and Wall pressure.
- Regular encounters teach distinct threat responses: Ash, Fog/Stain/Surge and Stone/Seal.
- Act I boss has a telegraphed Stone/Strike cadence and a route-preservation counterplay brief.
- Tests verify IDs, deterministic forecasts, tutorial constraints, sequence looping and boss pressure.

## Scope boundary

This is the first content slice, not the full 36 regular / 12 elite / 3 boss target. The encounter metadata gates onboarding, but a full run/map/reward orchestration layer and encounter UI remain subsequent work.

## Verification

`.github/workflows/efb007-encounters.yml` runs the complete Kotlin/JVM test suite and pure-JVM boundary check on exact SHA.


**Verified candidate:** `577c56df026837f1acacd4ad9dafc7153002e15e`  
**GitHub Actions run:** [45293372126](https://github.com/grep999/Apo/actions/runs/45293372126) — full Kotlin/JVM suite PASS; pure-JVM boundary PASS.
