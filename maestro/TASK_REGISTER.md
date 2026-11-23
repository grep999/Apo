# EMBERFALL Maestro Task Register

The machine-readable task seed is `maestro/EMBERFALL_TASKS_V2.json`; the epic register is `engineering/IMPLEMENTATION_WBS.json`. Detailed sequencing and release acceptance criteria are in `engineering/CODING_AGENT_ROADMAP.md` and `engineering/RELEASE_READINESS.md`.

## Task catalogue

| ID | Increment | Owner | Risk | Current portfolio state |
|---|---|---|---|---|
| EFB-000-BASELINE | Repository/source/build baseline | ARCHITECT | R1 | Merged; independent merged-tree review pending |
| EFB-001-RULES-ADR | Rules contract and P0 decisions | ARCHITECT | R1 | Merged; PO baseline present; review evidence reconciliation pending |
| EFB-002-BOARD-CORE | Deterministic headless board | BUILDER | R1 | Exact candidate CI verified; independent Mastery pending |
| EFB-003-TURN-COMBAT | Turn phases, player/enemy combat, intents | BUILDER | R1 | Grounding packet prepared; blocked on EFB-002 acceptance |
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

- EFB-002 workflow: `b856a095-5d26-4aeb-8c52-82f36dcde6e6`.
- Stage `MASTERY`, status `ACTIVE`, revision 5.
- Three Momentum work items complete; one independent Mastery work item pending.
- Review assignment is `PROPOSED`, not assigned or completed.
- Exact candidate: `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`.
- Exact-candidate Gradle/JUnit and pure-JVM checks are VERIFIED and passing; see `engineering/RELEASE_READINESS.md`.

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

**Do not start EFB-003 implementation until EFB-002 acceptance and Repetition are complete.**
