# EFB-000 — Repository, Source Inventory and Build Baseline

**Project:** EMBERFALL  
**Baseline date:** 2026-11-23  
**Task:** EFB-000-BASELINE  
**Status:** BUILDER COMPLETE — E00 workflow validates the exact PR head; independent review pending. The authoritative candidate SHA is the current PR #4 head, not a SHA embedded in this self-updating report.  
**Authority:** `handoff/emberfall-coding-agent-v1.0` and `references/EMBERFALL_GAME_SPEC_v0.2.txt`

## 1. Executive finding

Apo contains distinct project surfaces that must not be conflated:

1. `main` is a documentation/control branch, not the game source.
2. `handoff/emberfall-coding-agent-v1.0` is the v0.2 design and execution authority. It contains the game specification, WBS, ADR register, art references and Maestro bootstrap, but no buildable Android application.
3. `implementation/emberfall-latest-source` is a Kotlin/Android prototype. It contains reusable engineering patterns, but its active live loop is swap-match-3. The v0.2 specification requires connected-group Tap/Pop.
4. `design/efb-001-rules-adr-proposals` is the Product Owner's resolved ADR candidate. It resolves all 19 blocking rules decisions; the exact current candidate and its latest passing validation are tracked by PR #5 and its GitHub Actions checks. EFB-002 remains gated until the baseline and rules acceptance gates are satisfied.

**Baseline decision:** retain the Android implementation branch as a legacy prototype and source of reusable architecture. Do not treat it as the v0.2 implementation or merge its gameplay/balance changes into the handoff branch. No gameplay rule is silently imported from the prototype; any deliberately carried-forward behavior is recorded in a Product Owner ADR.

## 2. Current branch inventory

| Branch | Current head | Contents | Interpretation |
|---|---|---|---|
| `main` | `6d057b6a4307917340e5dcaf84c7513d01027c64` | Control/docs branch and CI workflows | Not a game source branch |
| `handoff/emberfall-coding-agent-v1.0` | `26f0e9afb6c175b1d2a26c55de8098ae5deab580` | v0.2 specification, WBS, open decision register, art references and Maestro bootstrap | Current design authority; no Android source |
| `qa/five-persona-first-run-audit` | `bbeb5215dbe455e92177756661af65123a70bfa2` | Handoff snapshot and simulated first-run critique | Design critique only; not human playtest evidence |
| `implementation/emberfall-latest-source` | `e3610946ecf4caf91f0e1d2a232af714b88e65c2` | Kotlin/Android source, engine, assets, tests and Gradle build files | Legacy prototype; inspect and selectively reuse |
| `feat/emberfall-multi-policy-balance-lab` | `4910e855adda91b87e0b02aa07e713c65f1cdddb` | Legacy reward-flow, support guard and policy matrix candidate | Draft PR #3; HOLD, do not merge |
| `audit/e00-repository-source-baseline` | Current PR #4 head (see PR metadata) | EFB-000 inventory, validator, Maestro packet and workflow | Draft PR #4; builder evidence passed, independent review pending |
| `design/efb-001-rules-adr-proposals` | Current PR #5 head (see PR metadata) | Product Owner ADR resolutions, governance charter, content queue and EFB-002/EFB-CONTENT packets | Draft PR #5; Product Owner rules baseline validator passed; independent review pending |

The handoff branch has no `app/`, `settings.gradle.kts`, root `build.gradle.kts`, Gradle wrapper or Android test source. Android build, lint and package gates are not applicable to that docs-only branch; they are not PASS.

## 3. Authoritative v0.2 source

- `references/EMBERFALL_GAME_SPEC_v0.2.txt` is the gameplay source of truth.
- `engineering/IMPLEMENTATION_WBS.json` is version 1.0, project EMBERFALL, Maestro version 0.3.0, with 17 epics.
- `engineering/OPEN_DECISIONS.md` on the handoff branch is the original 23-row register.
- `maestro/CORE_PACKET_TEMPLATES_V03.json`, `maestro/EMBERFALL_TASKS_V2.json` and `maestro/bootstrap_current_maestro.sh` define workflow integration.
- Bootstrap pins upstream Maestro v0.3.0 at `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.

### Core-rule divergence

The legacy implementation README and engine use a swap-based match-3 loop (`SwapCells`, line matching and cascades). The v0.2 spec selects connected-group Tap/Pop, vertical gravity and Wall pressure, and explicitly replaces swap-match-3. Prototype board/effect architecture, deterministic simulation, save logic and Android shell may be inspected for reusable patterns; its active board-resolution rules are not authoritative.

### Legacy content inventory

- 65 `CardDef` definitions.
- 22 `RelicDef` definitions.
- 8 Dhāraṇā support definitions.
- 8 Tantric artifact definitions.
- 3 `HeroClass` definitions.
- Existing 7×7 board and Android Compose client.

These are prototype counts, not v0.2 launch-content targets.

## 4. Decision-register reconciliation

The handoff branch's original `engineering/OPEN_DECISIONS.md` contains 23 ADR rows:

| Original handoff status | Count |
|---|---:|
| OPEN / BLOCKING | 19 |
| RESOLVED by platform policy check | 1 |
| OPEN (trademark/title) | 1 |
| PROPOSED | 2 |

The QA branch's simulated first-run summary reports 18 open/blocking decisions. This conflicts with the 19 OPEN/BLOCKING rows in the handoff register. EFB-000 records the discrepancy; the handoff register is the source to reconcile, not the older QA summary.

The EFB-001 Product Owner candidate now resolves all 19 blocking rules ADRs in individual records. Its validator passed on SHA `3c819430f0ee960cd96b5b94b1d8c33ca2da4912` in run `45165670116` (see PR #5). The handoff branch remains unchanged until the PR is accepted. ADR-021 title/trademark clearance remains open and non-blocking.

The five-persona report is a simulated review. It is not evidence from five human players and must not be cited as human usability validation.

## 5. Toolchain and build baseline

### Handoff branch

- No Android application module or Gradle build files exist.
- Android build, lint, Robolectric and emulator gates are NOT APPLICABLE to this branch at this stage; they are not PASS.
- The correct gate is specification/WBS/ADR/Maestro artifact validation.

### Legacy implementation branch

- Kotlin Android/Jetpack Compose application with a pure-JVM engine package.
- Gradle version catalog and wrapper properties exist; root `gradlew` and `gradle-wrapper.jar` are absent.
- GitHub Actions provisions JDK 21, Android SDK 36.1 and Gradle 9.3.1 directly.
- Earlier CI had one stale Robolectric reward-navigation expectation; it was corrected to require support/artifact resolution before next-room navigation.
- On legacy PR #3 head `4910e855adda91b87e0b02aa07e713c65f1cdddb`, the complete Android Verification workflow passed, including JVM/Room/audio/Robolectric tests, debug/release lint, debug/release APK and AAB packaging. This verifies the legacy prototype build only; it does not validate v0.2 group-pop gameplay.
- The legacy matched-seed matrix previously passed 900 campaigns (20 seeds × 3 classes × 15 policy cells), 900 wins and 0 incomplete. It is diagnostic only; every policy saturated progression and the Deck Curator policy matched Balanced.

## 6. Legacy prototype CI — explicitly out of v0.2 scope

Draft PR #3 is retained as a diagnostic record and remains **HOLD — do not merge**. Its branch still drives live combat and bot simulation through SwapCells. The current Android verification rerun is a legacy code-quality check, not v0.2 gameplay verification. No XP thresholds, Cinders values or production balance numbers are changed from the matrix.

## 7. Reusable prototype architecture candidates

Subject to code-level review and v0.2 contracts:
- Pure-JVM deterministic domain engine and seeded simulation harness.
- Data-driven card/effect definitions and explicit effect queue.
- Enemy intent and boss state modeling.
- Run-state snapshots, Room persistence and migration tests.
- Android Compose shell, asset loading and audio adapter boundaries.
- Existing test organization and GitHub-hosted Android toolchain workaround.

Not reusable without replacement or explicit ADR:
- Swap-match-3 board resolution and cascade rules.
- Legacy reward/economy balance values.
- Legacy content counts and class starter pools.
- Existing simulation bots as evidence of v0.2 balance.

## 8. EFB-000 acceptance checklist

| Requirement | Status | Evidence / next action |
|---|---|---|
| Canonical spec and WBS identified | COMPLETE | v0.2 spec and 17-epic WBS |
| Branch/source inventory recorded | COMPLETE | Section 2 |
| Prototype identified and classified | COMPLETE | Section 3 |
| Legacy/v0.2 rules divergence recorded | COMPLETE | Section 3 |
| ADR register counted and QA discrepancy logged | COMPLETE | Section 4 |
| Toolchain/build applicability recorded | COMPLETE | Section 5 |
| Upstream Maestro v0.3 pinned | COMPLETE | Bootstrap pin |
| Maestro bootstrap, contracts and GateRunner checks pass in CI | COMPLETE | Latest successful E00 workflow for the current PR #4 head; exact checkout SHA and VERIFIED evidence are asserted and uploaded by the workflow |
| Independent reviewer verifies exact candidate SHA | PENDING | @copilot review requested; response pending |
| EFB-000 marked complete in Maestro state | PENDING | Maestro artifact remains ACTIVE / GROUNDING; do not complete before independent review |

## 9. EFB-000 GitHub Actions evidence

**Latest evidence:** Use the latest successful `Emberfall Handoff and Maestro v0.3 Validation` run attached to the current PR #4 head. The workflow checks out the event's exact head SHA, emits `baseline_sha`, asserts GateRunner `ran_on_sha` and VERIFIED evidence, and uploads the Maestro state plus reports as `emberfall-efb-000-maestro-evidence`. Do not treat a prior run as evidence for a later head.

- Pinned upstream Maestro v0.3.0 bootstrap completed at `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.
- Maestro unit tests: 19 passed.
- Maestro contract validation: 3/3 passed.
- Adapter inventory and echo adapter health check completed.
- Apo handoff GateRunner smoke check: VERIFIED; runner identity, `ran_on_sha`, and log hashes are recorded in the artifact and asserted against the checked-out candidate.
- Source inventory validator: 17 WBS epics, 17 Maestro tasks, 23 ADR rows, 19 OPEN/BLOCKING.
- Android build gate: `NOT_APPLICABLE_NO_ANDROID_SOURCE`; explicitly not a build pass.
- Maestro state artifact: workflow `d209223b-14d0-4137-8be0-4a5214e407aa`, ACTIVE / GROUNDING, revision 1. It is intentionally not advanced to completion pending independent review.

## 10. EFB-001 and EFB-002 sequencing

The Product Owner has resolved the 19 blocking rules decisions in PR #5; its latest candidate and validation status are tracked on that PR. Specialist output remains proposal-only; no content is accepted. EFB-002 board-core packet is prepared but marked `BLOCKED_PENDING_EFB000_INDEPENDENT_REVIEW`. It explicitly forbids live SwapCells and authorizes no implementation until EFB-000 is accepted.

After EFB-000 independent review and PR #5 acceptance, begin EFB-002 group-pop board core. Do not implement card breadth or content scale before the deterministic board vertical slice passes.

## 11. Current EFB-000 disposition

Builder-side baseline and CI evidence are complete. EFB-000 is **ready for independent review**, not complete. The reviewer must inspect the current PR #4 head and its matching successful E00 workflow artifact, verifying source classification, v0.2/legacy rule divergence, ADR-count discrepancy, workflow/validator correctness and exact evidence. Any new commit supersedes prior review evidence and requires a fresh workflow run and review. Only after approval should Maestro advance from GROUNDING.
