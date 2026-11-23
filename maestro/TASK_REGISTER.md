# EMBERFALL Maestro Task Register

The machine-readable task seed is `maestro/EMBERFALL_TASKS_V2.json`; the epic register is `engineering/IMPLEMENTATION_WBS.json`. Detailed sequencing and release acceptance criteria are in `engineering/CODING_AGENT_ROADMAP.md` and `engineering/RELEASE_READINESS.md`.

Agent dispatch, role boundaries, prompt/return contracts, state continuity, security and recovery are governed by [`engineering/AGENT_ORCHESTRATION.md`](../engineering/AGENT_ORCHESTRATION.md). A first Copilot cloud-agent dispatch/status MVP is assembled in `.github/workflows/emberfall-agent-{dispatch,status}.yml`; it requires `COPILOT_AGENT_TOKEN` and successful Actions receipts before any session is claimed started.

## Task catalogue

| ID | Increment | Owner | Risk | Current portfolio state |
|---|---|---|---|---|
| EFB-000-BASELINE | Repository/source/build baseline | ARCHITECT | R1 | Historical baseline retained for audit. Product Owner explicitly excluded legacy prototype and associated source/assets from clean product baseline; no rights clearance is sought for excluded material. Not an EFB-003 dependency |
| EFB-001-RULES-ADR | Rules contract and P0 decisions | ARCHITECT | R1 | Merged; Product Owner ADR validator PASS; no formal review submission or native Maestro workflow |
| EFB-002-BOARD-CORE | Deterministic headless board | BUILDER | R1 | **Accepted by Product Owner** at exact head 6b6e139; one review round completed, further independent Mastery waived by owner; exact CI PASS; Repetition recorded. PR #14 remains draft; no additional EFB-002 review is required absent new evidence |
| EFB-003-TURN-COMBAT | Turn phases, player/enemy combat, intents | BUILDER | R1 | **IN PROGRESS — PR #15** clean-room candidate `fbb8b07c`; based on accepted EFB-002 SHA `6b6e139`. Prior candidate `8dd0a03c` passed full Gradle/JUnit and pure-JVM run `45273205685`; final candidate run `45273418357` queued. Independent Mastery and Repetition pending. Legacy prototype excluded |
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

- EFB-000 workflow: `9dcb920b-4da4-4e96-bd96-584db5d3c4ab`; ACTIVE / GROUNDING / revision 1. Product Owner has excluded the legacy prototype and all associated source/assets from the product baseline. EFB-000 provenance is retained for audit only; no rights clearance is pursued for excluded material.
- EFB-001: Product Owner ADR validation run `45180407668` passed; a subsequent cross-check run `45261280912` also passed on the current EFB-002 PR head. No native EFB-001 Maestro workflow or formal review submission exists. Review input: `maestro/reviews_v03/EFB-001-RULES-ADR.review-input.json`.
- EFB-002 workflow artifacts are run-scoped; the proposed reviewer assignment was not dispatched and durable cross-run state is not proven. The Product Owner subsequently accepted exact candidate `6b6e139890a205ead337a94226464a0b5ddd82da` and waived any further EFB-002 Mastery round; Repetition is recorded in `maestro/packets_v03/EFB-002-BOARD-CORE.repetition.json`. Native Maestro workflow reconciliation remains pending.
- EFB-002 accepted candidate: PR #14, head `6b6e139890a205ead337a94226464a0b5ddd82da`; exact-head GateRunner run `45265105467`, Board Core run `45265105730`, ADR validator run `45265105426` all passed. Product Owner accepted after one feedback round and waived further EFB-002 Mastery. The half-Ink defect and hue-weight oracle were corrected.
- EFB-000 merged-tree review input: `maestro/reviews_v03/EFB-000-BASELINE.review-input.json`.
- EFB-002 exact-candidate Gradle/JUnit and pure-JVM checks are VERIFIED and passing on head `6b6e139...`; half-Ink payout correction is included. Formatting/lint tooling is not configured and is not claimed. See `engineering/RELEASE_READINESS.md`.

## EFB-003 clean-core candidate

- PR #15: `https://github.com/grep999/Apo/pull/15`, branch `feat/efb003-turn-combat-clean`, accepted EFB-002 base `6b6e139890a205ead337a94226464a0b5ddd82da`.
- Candidate code SHA: `fbb8b07c134b77825cedac3f65389be13f11fd15`. It adds the deterministic turn/combat reducer, player resources, two-intent preview, ten intent contracts, Wall/Seal integration, Card Pop seam, immutable snapshots, versioned replay and run-seed binding.
- Earlier CI failures were found and corrected: one stale Seal/Stain incoming-row assertion and a misplaced test-class brace. Prior candidate `8dd0a03c` passed run `45273205685`; final-candidate run `45273418357` is queued; no final-candidate PASS is claimed.
- Independent Mastery and Repetition remain required before acceptance/merge.
- Legacy prototype and assets are explicitly excluded; no legacy code/assets are imported.

## Agent orchestration MVP

- Dispatch workflow: `.github/workflows/emberfall-agent-dispatch.yml`; one JSON task packet per push under `maestro/dispatch-requests/`.
- Status workflow: `.github/workflows/emberfall-agent-status.yml`; task IDs are queried through the Copilot cloud-agent tasks API.
- Initial packets committed: AO-01 workflow/Maestro inventory, AO-02 Maestro SQLite/WAL resume architecture, AO-03 dispatch security review.
- Required secret: `COPILOT_AGENT_TOKEN` (user-to-server fine-grained PAT; repository Actions, Contents, Issues and Pull Requests read/write). Copilot cloud agent must be enabled.
- Dispatch state is NOT yet confirmed. Verify each Actions run and its 30-day receipt artifact. Maestro SQLite cross-run resume remains unimplemented.

## Workflow-control-plane actions

- [x] Product Owner explicitly excluded the legacy prototype and associated assets; no legacy rights clearance is sought for excluded material. Keep EFB-000 provenance evidence as historical archive.
- [ ] Reconcile EFB-001 into a native Maestro task state without reopening accepted ADRs.
- [ ] Persist and resume the same Maestro DB/workflow ID across candidate pushes; current PR workflow creates a fresh database each run.
- [ ] Configure a real independent-review adapter; PROPOSED is not ASSIGNED. Both current EFB-000/EFB-002 Copilot dispatch attempts returned HTTP 412; no reviewer session was created.
- [ ] Require EFB-003 exact candidate file hashes, exact SHA, VERIFIED gates, distinct independent reviewer identity and Repetition before acceptance.

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

**EFB-002 is accepted and its Repetition is recorded. Do not start EFB-003 until EFB-000 merged-tree review/rights disposition is complete and durable Maestro state continuity plus a working independent-review route are operational.**
