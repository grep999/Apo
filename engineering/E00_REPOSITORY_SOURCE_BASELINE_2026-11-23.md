# EFB-000 — Repository, Source Inventory and Build Baseline

**Project:** EMBERFALL  
**Baseline date:** 2026-11-23  
**Task:** EFB-000-BASELINE  
**Status:** BUILDER COMPLETE — E00 validation passed on candidate SHA `694cb36a532a241cfc073a6260ebc50503be4804`; independent review pending  
**Authority:** `handoff/emberfall-coding-agent-v1.0` and `references/EMBERFALL_GAME_SPEC_v0.2.txt`

## 1. Executive finding

Apo contains three materially different project surfaces. They must not be conflated:

1. `main` is a documentation/control branch. It contains no Android application source.
2. `handoff/emberfall-coding-agent-v1.0` is the v0.2 design and execution handoff. It contains the game specification, WBS, ADR register, asset references and Maestro bootstrap, but no buildable Android application.
3. `implementation/emberfall-latest-source` is an existing Kotlin/Android prototype. It is a useful architecture and systems reference, but its active board loop is swap-match-3. The v0.2 specification requires connected-group Tap/Pop resolution and explicitly replaces the swap-match-3 rules.

**Decision for this baseline:** retain the implementation branch as a legacy prototype and source of reusable architecture; do not treat it as the v0.2 implementation or merge its gameplay/balance changes into the handoff branch. No gameplay rule is silently imported from the prototype.

## 2. Branch inventory at baseline

| Branch | Commit | Contents | Baseline interpretation |
|---|---|---|---|
| `main` | `1fc6d313728f46ab9c478b643e52374ed137d65b` | Control/docs branch plus three CI workflows | Not a game source branch |
| `handoff/emberfall-coding-agent-v1.0` | `af7ca237b85c7b8fc2ecb38ce163d46c259e1b1d` | v0.2 specification, WBS, decision register, art references, Maestro v0.3 bootstrap and EFB-000 workflow | Current design authority; no Android source |
| `qa/five-persona-first-run-audit` | `bbeb5215dbe455e92177756661af65123a70bfa2` | Handoff snapshot plus simulated first-run audit and QA notes | Design critique evidence; not human playtest evidence |
| `implementation/emberfall-latest-source` | `e3610946ecf4caf91f0e1d2a232af714b88e65c2` | Kotlin/Android source, engine, assets, tests and Gradle build files | Legacy prototype to inspect and selectively reuse |
| `feat/emberfall-multi-policy-balance-lab` | `911e2272da14371c1fc271ead406c8bcaa04b145` | Legacy reward-flow/persistence and policy-matrix candidate | Draft PR #3; HOLD, do not merge |
| `audit/e00-repository-source-baseline` | `694cb36a532a241cfc073a6260ebc50503be4804` | EFB-000 inventory, validator, Maestro packet and workflow | Draft PR #4; builder evidence passed, review pending |

At the original handoff baseline SHA `fe94e61e...`, the handoff branch had 30 tracked paths; it now has 31 after adding the EFB-000 workflow. It has no `app/`, `settings.gradle.kts`, root `build.gradle.kts`, Gradle wrapper or Android test source. The legacy implementation branch has 224 tracked paths and a Kotlin Android application.

## 3. Authoritative v0.2 source

- `references/EMBERFALL_GAME_SPEC_v0.2.txt` is the gameplay source of truth.
- `engineering/IMPLEMENTATION_WBS.json` is version 1.0, project `EMBERFALL`, Maestro version `0.3.0`, with 17 epics.
- `engineering/OPEN_DECISIONS.md` is the active decision register.
- `maestro/CORE_PACKET_TEMPLATES_V03.json`, `maestro/EMBERFALL_TASKS_V2.json` and `maestro/bootstrap_current_maestro.sh` define the project workflow integration.
- The bootstrap script pins upstream Maestro v0.3.0 at commit `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.

### Core-rule divergence

The legacy implementation README and engine use a swap-based match-3 board loop (`SwapCells`, match/cascade resolution). The v0.2 specification defines a connected-group Tap/Pop loop, vertical gravity and Wall pressure, and explicitly says to replace swap-match-3 rules. The prototype's existing board, effect, deterministic simulation, save and Android architecture may be inspected for reusable patterns; its board-resolution rules are not authoritative.

### Legacy content inventory (source inspection)

- 65 `CardDef` definitions.
- 22 `RelicDef` definitions.
- 8 Dhāraṇā support definitions.
- 8 Tantric artifact definitions.
- 3 `HeroClass` definitions.
- Existing 7×7 board and Android Compose client.

These are prototype counts, not v0.2 launch-content targets.

## 4. Decision-register baseline

The handoff branch's `engineering/OPEN_DECISIONS.md` contains 23 ADR rows:

| Status | Count |
|---|---:|
| OPEN / BLOCKING | 19 |
| RESOLVED by platform policy check | 1 |
| OPEN (trademark/title) | 1 |
| PROPOSED | 2 |

The QA branch's first-run simulation summary reports 18 open/blocking decisions. That conflicts with the 19 OPEN / BLOCKING rows in the current handoff register. EFB-000 records the discrepancy; the register is the source to reconcile, not the older QA summary.

The five-persona report is a simulated review. It is not evidence from five human players and must not be cited as human usability validation.

## 5. Toolchain and build baseline

### Handoff branch

- No Android application module or Gradle build files exist.
- Android build, lint, Robolectric and emulator gates are **NOT APPLICABLE** to this branch at this stage; they are not PASS.
- The correct immediate gate is specification/WBS/ADR/Maestro artifact validation.

### Legacy implementation branch

- Kotlin Android/Jetpack Compose application with a pure-JVM engine package.
- Gradle version catalog and `gradle/wrapper/gradle-wrapper.properties` exist.
- Root `gradlew` and `gradle/wrapper/gradle-wrapper.jar` are absent.
- The local execution environment lacked Gradle, Android SDK and ADB.
- GitHub Actions workaround provisions JDK 21, Android SDK 36.1 and Gradle 9.3.1 directly, avoiding the missing wrapper binary.
- The first full CI compile completed and 91 unit/Robolectric tests executed: 90 passed and one legacy inline-victory fixture failed because it expected advancement while the new four-channel reward contract still had unresolved support/artifact choices. Lint and packaging were not reached in that run.
- A matched-seed policy matrix was started against the legacy prototype. Its results, when available, are diagnostic for that prototype only and must not be used to tune the v0.2 game.

## 6. Existing legacy CI work — explicitly out of v0.2 scope

The investigation added Android and balance-matrix workflows to validate the legacy implementation branch. They are operational workarounds for that branch's missing wrapper/SDK environment; they do not establish that the docs-only handoff branch is buildable.

Draft PR #3 is retained as a diagnostic record and has been commented **HOLD — do not merge** pending source reconciliation. No merge or release has been performed.

## 7. Reusable prototype architecture candidates

Subject to code-level review and v0.2 contracts, the following are candidates for reuse rather than automatic imports:

- Pure-JVM deterministic domain engine and seeded simulation harness.
- Data-driven card/effect definitions and explicit effect queue.
- Enemy intent and boss state modeling.
- Run-state snapshots, Room persistence and migration tests.
- Android Compose client shell, asset loading and audio adapter boundaries.
- Existing test organization and GitHub-hosted Android toolchain workaround.

The following are not reusable without replacement or explicit ADR approval:

- Swap-match-3 board resolution and cascade rules.
- Reward/economy balance values from the legacy prototype.
- Legacy content counts and class starter pools.
- Existing simulation bot policy as evidence of v0.2 balance.

## 8. EFB-000 acceptance checklist

| Requirement | Status | Evidence / next action |
|---|---|---|
| Canonical spec and WBS identified | COMPLETE | v0.2 spec and 17-epic WBS |
| Branch/source inventory recorded | COMPLETE | Section 2 |
| Prototype identified and classified | COMPLETE | Section 3 |
| Legacy/v0.2 rules divergence recorded | COMPLETE | Section 3 |
| ADR register counted and discrepancy logged | COMPLETE | Section 4 |
| Toolchain/build applicability recorded | COMPLETE | Section 5 |
| Upstream Maestro v0.3 pinned | COMPLETE | `bootstrap_current_maestro.sh` pin |
| Maestro bootstrap, contracts and GateRunner checks pass in CI | COMPLETE | Run `45150676282`; pinned Maestro tests 19/19, contracts 3/3, GateRunner smoke VERIFIED on candidate SHA |
| Independent reviewer verifies exact candidate SHA | PENDING | Must be a different reviewer from builder; candidate `3159b6231eb806fabe10c61a4aa1500a0f91f40d` |
| EFB-000 marked complete in Maestro state | PENDING | Maestro artifact state is ACTIVE / GROUNDING, revision 1; do not complete before independent review |

## 9. Next increment

After the E00 validation workflow passes, proceed to **EFB-001-RULES-ADR**. Draft the blocking rule decisions as explicit ADR proposals with examples and testable acceptance criteria. Do not implement EFB-002 board behavior until the P0 rule contract is approved.

No game code or gameplay rules are changed by this baseline report.


## 10. Validation workflow placement

The EFB-000 workflow is present on the default branch, the handoff base branch and this audit candidate so GitHub can evaluate push and pull-request events. It validates the docs-led handoff and pinned Maestro runtime; it deliberately does not invoke Android Gradle tasks while the handoff branch has no Android source.


## 10. EFB-000 GitHub Actions evidence

Workflow run: `45150948444`  
Candidate SHA: `694cb36a532a241cfc073a6260ebc50503be4804`  
Artifact digest: `sha256:074045055acd1e043f07a3c177b5c81a10c163db06d9d0864591c0bb6b69e2bb`

- Pinned upstream Maestro v0.3.0 bootstrap completed at `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.
- Maestro unit tests: **19 passed**.
- Maestro contract validation: **3/3 passed**.
- Adapter inventory and echo adapter health check completed.
- Apo handoff GateRunner smoke check: **passed**, `evidence_class=VERIFIED`, runner identity `gate-runner@runnervm0hi8g`, `ran_on_sha=694cb36a532a241cfc073a6260ebc50503be4804`, log hash `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
- Source inventory validator: **passed** — 17 WBS epics, 17 Maestro tasks, 23 ADR rows, 19 OPEN/BLOCKING.
- Android build gate: `NOT_APPLICABLE_NO_ANDROID_SOURCE`; this is explicitly not a build pass.
- Maestro state artifact: workflow `994eda8e-a44c-4e67-a0b4-5856c9977fbe`, ACTIVE / GROUNDING, revision 1. The state is intentionally not advanced to completion pending independent review.

## 11. Legacy prototype matrix — diagnostic only

The legacy-only matrix workflow run `45147504116` completed **900 campaigns** (20 seeds × 3 classes × 15 route/combat cells): **900 wins, 0 incomplete**. This is bot simulation of the old swap-match-3 prototype, not human playtesting and not evidence for v0.2.

| Route policy | Campaigns | Wins | Incomplete | Avg level | Avg Cinders | Avg deck |
|---|---:|---:|---:|---:|---:|---:|
| Balanced | 180 | 180 | 0 | 13.14 | 1,592.96 | 18.43 |
| Survival | 180 | 180 | 0 | 13.14 | 1,594.52 | 18.43 |
| Elite hunter | 180 | 180 | 0 | 13.67 | 1,751.18 | 18.09 |
| Treasure seeker | 180 | 180 | 0 | 13.18 | 1,596.57 | 18.44 |
| Deck curator | 180 | 180 | 0 | 13.14 | 1,592.96 | 18.43 |

Every route/combat cell averaged all 12 passives, all 3 support sockets and all 8 artifacts. `DECK_CURATOR` produced exactly the same aggregate metrics as `BALANCED` in this sample, so it did not demonstrate a distinct policy outcome. The legacy prototype is progression-saturated under these bot policies. No balance values were changed from this evidence.

## 12. Current EFB-000 disposition

Builder-side baseline and CI evidence are complete. EFB-000 is **ready for independent review**, not complete. The exact candidate SHA recorded by the latest successful E00 run is `694cb36a532a241cfc073a6260ebc50503be4804`; any subsequent candidate commit must rerun the E00 workflow before review. The reviewer must verify the source classification, the v0.2/legacy rule divergence, the ADR-count discrepancy, and the GitHub Actions evidence. Only after that review should the Maestro workflow advance from GROUNDING.

