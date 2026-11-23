# EFB-000 — Repository, Source Inventory and Build Baseline

**Project:** EMBERFALL  
**Baseline date:** 2026-11-23  
**Task:** EFB-000-BASELINE  
**Status:** IN PROGRESS — repository reconciliation documented; Maestro validation gate pending  
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
| `main` | `beace27f8de11cbac0d1680ad4cb58a8c1591789` | Control/docs branch plus CI workflow files added during legacy investigation | Not a game source branch |
| `handoff/emberfall-coding-agent-v1.0` | `fe94e61e3d676a57d45aec5f11022c54dbb79b95` | v0.2 specification, WBS, decision register, art references, Maestro v0.3 bootstrap and task packets | Current design/implementation authority |
| `qa/five-persona-first-run-audit` | `bbeb5215dbe455e92177756661af65123a70bfa2` | Handoff snapshot plus simulated first-run audit and QA notes | Design critique evidence; not human playtest evidence |
| `implementation/emberfall-latest-source` | `e3610946ecf4caf91f0e1d2a232af714b88e65c2` | Kotlin/Android source, engine, assets, tests and Gradle build files | Legacy prototype to inspect and selectively reuse |
| `feat/emberfall-multi-policy-balance-lab` | `911e2272da14371c1fc271ead406c8bcaa04b145` | Legacy reward-flow/persistence and policy-matrix candidate | Draft PR #3; HOLD, do not merge |

The handoff branch has 30 tracked paths and no `app/`, `settings.gradle.kts`, root `build.gradle.kts`, Gradle wrapper or Android test source. The legacy implementation branch has 224 tracked paths and a Kotlin Android application.

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
| Maestro bootstrap, contracts and GateRunner checks pass in CI | PENDING | E00 workflow run required |
| Independent reviewer verifies exact candidate SHA | PENDING | Must be a different reviewer from builder |
| EFB-000 marked complete in Maestro state | PENDING | Only after verified gates and review |

## 9. Next increment

After the E00 validation workflow passes, proceed to **EFB-001-RULES-ADR**. Draft the blocking rule decisions as explicit ADR proposals with examples and testable acceptance criteria. Do not implement EFB-002 board behavior until the P0 rule contract is approved.

No game code or gameplay rules are changed by this baseline report.
