# EMBERFALL Maestro Task Register

The machine-readable task seed is `maestro/EMBERFALL_TASKS_V2.json`; the epic register is `engineering/IMPLEMENTATION_WBS.json`. Detailed sequencing and release acceptance criteria are in `engineering/CODING_AGENT_ROADMAP.md` and `engineering/RELEASE_READINESS.md`.

Agent dispatch, role boundaries, prompt/return contracts, state continuity, security and recovery are governed by [`engineering/AGENT_ORCHESTRATION.md`](../engineering/AGENT_ORCHESTRATION.md). A first Copilot cloud-agent dispatch/status MVP is assembled in `.github/workflows/emberfall-agent-{dispatch,status}.yml`; it requires `COPILOT_AGENT_TOKEN` and successful Actions receipts before any session is claimed started.

## Task catalogue

| ID | Increment | Owner | Risk | Current portfolio state |
|---|---|---|---|---|
| EFB-000-BASELINE | Repository/source/build baseline | ARCHITECT | R1 | Merged; independent merged-tree review pending; governance follow-up revalidation PASS; license/IP rights still unconfirmed |
| EFB-001-RULES-ADR | Rules contract and P0 decisions | ARCHITECT | R1 | Merged; Product Owner ADR validator PASS; no formal review submission or native Maestro workflow |
| EFB-002-BOARD-CORE | Deterministic headless board | BUILDER | R1 | PR #14 draft; exact head 6b6e139 PASS on Gradle/JUnit, pure-JVM and ADR validation; half-Ink payout correction included; Mastery active; reviewer assignment PROPOSED, not assigned |
| EFB-003-TURN-COMBAT | Turn phases, player/enemy combat, intents | BUILDER | R1 | Grounding packet prepared; blocked on EFB-002 Mastery/Repetition, EFB-000 review, and durable Maestro dispatch/state readiness |
| EFB-004-CARD-EFFECTS | Typed data-driven card/effect engine | BUILDER | R1 | Not started; blocked on EFB-002/003 |
| EFB-005-CLASSES-STARTER | Three classes and starter decks | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-004 |
| EFB-006-ENEMIES-BOSSES | Enemies, elites, phased bosses | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-003/004 |
| EFB-007-RUN-PROGRESSION | Map, rewards, shops, bonfires, events, covenants | BUILDER | R1 | Not started; blocked on EFB-005/006 |
| EFB-008-SHADE-NARRATIVE | Death, Shade, Remnants, endings | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-007 |
| EFB-009-ANDROID-CLIENT | Android app, portrait combat, touch UX | BUILDER | R1 | Not started; blocked on EFB-002/003/004 |
| EFB-010-ART-ASSET-PIPELINE | Asset manifest, integration, atlases, VFX | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-009 and approved art |
| EFB-011-AUDIO-HAPTICS | Adaptive music, SFX, lighting response, haptics | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-009/010 |
| EFB-012-SAVE-REPLAY | Versioned persistence and deterministic replay | BUILDER | R2 | Not started; blocked on EFB-002/007 |
| EFB-013-BALANCE-LAB | Bots, sweeps, balance metrics, exploit tests | DOMAIN_SPECIALIST | R1 | Not started; blocked on EFB-004/006/007/012 |
| EFB-014-FULL-CONTENT | Launch content catalogue | DOMAIN_SPECIALIST | R1 | Not started; blocked on classes/enemies/progression/narrative/balance |
| EFB-015-BACKEND | Optional daily seeds, leaderboard, replay validation | BUILDER | R2 | Not started; blocked on EFB-012/013; offline-first |
| EFB-016-QA-RELEASE | Device, performance, accessibility and launch QA | ARCHITECT | R2 | Not started; blocked on all required product deliverables |
| EFB-ART-UI-BASELINE-01 | Integrate approved visual system as native Android layers | BUILDER | R1 | Not started; waits for Android client and approved asset handoff |

## Current Maestro state

- EFB-000 workflow: `9dcb920b-4da4-4e96-bd96-584db5d3c4ab`; ACTIVE / GROUNDING / revision 1; two READY work items; no assignments. Merged-tree review is outstanding; pre-review identified absent LICENSE/COPYING/NOTICE files in both the handoff and legacy trees, so legacy code reuse is not cleared.
- EFB-001: Product Owner ADR validation run `45180407668` passed; a subsequent cross-check run `45261280912` also passed on the current EFB-002 PR head. No native EFB-001 Maestro workflow or formal review submission exists. Review input: `maestro/reviews_v03/EFB-001-RULES-ADR.review-input.json`.
- EFB-002 latest run-scoped workflow: `ea293bf8-83a8-4d49-a6fd-ac722008f0b4`; ACTIVE / MASTERY / revision 5; three Momentum items complete; independent-review assignment `4133762a-64a2-46e7-bdc1-7822e0090ad8` is PROPOSED, not assigned. This is an artifact snapshot, not durable cross-run state.
- EFB-002 current candidate: PR #14, head `6b6e139890a205ead337a94226464a0b5ddd82da`; original remediation commit `6932b747d78f9310ecb859cc9079cb2fc28ac9bf` is preserved in its ancestry. Exact-head GateRunner run `45265105467`, Board Core run `45265105730`, ADR validator run `45265105426` all passed. The half-Ink truncation defect was corrected and golden hue-weight tests strengthened. Review input with current blob hashes and artifacts: `maestro/reviews_v03/EFB-002-BOARD-CORE.review-input.json`.
- EFB-000 merged-tree review input: `maestro/reviews_v03/EFB-000-BASELINE.review-input.json`.
- EFB-002 exact-candidate Gradle/JUnit and pure-JVM checks are VERIFIED and passing on head `6b6e139...`; half-Ink payout correction is included. Formatting/lint tooling is not configured and is not claimed. See `engineering/RELEASE_READINESS.md`.

## Agent orchestration MVP

- Dispatch workflow: `.github/workflows/emberfall-agent-dispatch.yml`; one JSON task packet per push under `maestro/dispatch-requests/`.
- Status workflow: `.github/workflows/emberfall-agent-status.yml`; task IDs are queried through the Copilot cloud-agent tasks API.
- Initial packets committed: AO-01 workflow/Maestro inventory, AO-02 Maestro SQLite/WAL resume architecture, AO-03 dispatch security review.
- Required secret: `COPILOT_AGENT_TOKEN` (user-to-server fine-grained PAT; repository Actions, Contents, Issues and Pull Requests read/write). Copilot cloud agent must be enabled.
- Dispatch state is NOT yet confirmed. Verify each Actions run and its 30-day receipt artifact. Maestro SQLite cross-run resume remains unimplemented.

## Workflow-control-plane actions

- [ ] Complete EFB-000 independent merged-tree review; explicitly record the missing license/IP provenance and prohibit legacy code reuse until rights are confirmed. Copilot cloud reviewer dispatch is blocked by HTTP 412 insufficient premium quota.
- [ ] Reconcile EFB-001 into a native Maestro task state without reopening accepted ADRs.
- [ ] Persist and resume the same Maestro DB/workflow ID across candidate pushes; current PR workflow creates a fresh database each run.
- [ ] Configure a real independent-review adapter; PROPOSED is not ASSIGNED. Both current EFB-000/EFB-002 Copilot dispatch attempts returned HTTP 412; no reviewer session was created.
- [ ] Require candidate file hashes, exact SHA, VERIFIED gates, distinct reviewer identity and Repetition before acceptance.

## Graphics task baseline

Use `graphics/MAESTRO_TASK_EFB_ART_UI_BASELINE_V2.json` and the exact image/archive hashes in `graphics/ASSET_MANIFEST_SUMMARY.json`. The task remains INBOX until the Android application repository and approved asset handoff are available.

## Per-task Maestro loop

1. Inspect the current repository, source, accepted decisions and dependency state.
2. Start one Maestro workflow per coherent increment with the real Git workspace and risk level.
3. Ground the task with sources, constraints, dependencies, risks, acceptance and a bounded next increment.
4. Begin Momentum and seed/review bounded research, implementation and gate work items.
5. Build in an isolated branch/worktree; record base SHA, candidate SHA, changed paths and builder identity.
6. Run only applicable project-specific GateRunner checks on the exact candidate. Unrun checks remain `not_run`.
7. Deliver Momentum with exact candidate reference, required VERIFIED evidence, file hashes and known risks.
8. Submit Mastery to an independent reviewer whose identity differs from the builder; review the exact candidate.
9. Resolve findings, record Repetition, then update the portfolio and task state from evidence.

The upstream Maestro engine persists workflow state and specialist dispatch packets; it does not start agents by itself. An adapter must be configured and observable before a task is claimed assigned/running. A `PROPOSED` assignment is not a reviewer.

**Do not start EFB-003 implementation until EFB-002 acceptance and Repetition are complete and the review-dispatch path is operational.**
