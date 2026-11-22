# EMBERFALL — Maestro Workflow Governance

This repository adopts the four-stage **Maestro** engineering governance lifecycle (`Grounding -> Momentum -> Mastery -> Repetition`, aligned with `https://github.com/grep999/maestro` and `ANDROID_ART_INTEGRATION_HANDOFF.md`) to prevent silent design drift, premature content expansion, and unverified build claims.

## 1. The Four Stages

1. **Grounding**
   - Record exact specifications, ADR constraints, missing upstream docs (`Design Bible v0.2`, `CH01`), source asset hashes (`class_roster_painterly_1__23f2a842.png`, `ART_BASELINE_LOCK v0.4`), and exact code/defect inventory before editing.
   - Lock scope boundaries: pure Kotlin/JVM `com.example.engine`, three canonical classes (`Ashen Monk`, `Quarry Witch`, `The Unlit`), five hues (`Ember`, `Gold`, `Moss`, `Tide`, `Violet`), Shade as Row 5 encounter, zero meta-currency.

2. **Momentum**
   - Execute small, verifiable increments strictly in corrective order:
     1. Ashen Monk Vigil-burn identity + removal of multi-hit counterattack exploit (`P0.1`)
     2. Brand / Fog / Seal exact semantics (`P0.2`)
     3. Wall Overflow exact predicate & penalties (`P0.3`)
     4. Echo single primary effect (`P0.4`)
     5. Unlit Covenant + Dread 3-turn Ash-only true ending (`P0.5`)
     6. Transactional Command transition guards (`P0.6`)
     7. ADR-014 Unlit starter, ADR-013 node scaling, Map Event nodes & path-composition validator, full Shade deck intents, deck-accounted no-move reshuffle (`P1`)
     8. Defeat routing, Fog/Seal/Brand/Generic-Ink UI, Room migrations, snapshot migration + strict fail, serialized persistence writes, extracted painterly class portraits (`UI-P1` / `EFB-ART-UI-BASELINE-01`)
   - GateRunner requires actual execution of `compile_applet` and `gradle :app:testDebugUnitTest --console=plain` with raw pass/fail counts.

3. **Mastery (Independent Verification)**
   - Verify every P0/P1 rule against the contract trace matrix (`ADR -> Model -> Engine -> UI -> Test`), NOT against legacy bug-certifying tests.
   - Audit zero `android.*`/`androidx.*` imports inside `com.example.engine`.
   - Audit anti-drift compliance (zero Pyromancer/Cinderknight/Ashweaver/Vanguard/Warden class names; zero mocked completion claims).

4. **Repetition**
   - Record findings, explicit provisional assumptions awaiting `Design Bible v0.2` / `CH01`, any residual defects, and the next bounded increment.

## 2. Active Workflow Manifests
- `.maestro/workflows/EFB-RULES-CORRECTIVE-01.yaml`
- `.maestro/workflows/EFB-ART-UI-BASELINE-01.yaml`
