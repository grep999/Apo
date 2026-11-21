# EMBERFALL Maestro Task Register

The machine-readable register is `engineering/IMPLEMENTATION_WBS.json`; its CSV view is `engineering/IMPLEMENTATION_WBS.csv`. Detailed sequencing, acceptance criteria and dependencies are in `engineering/CODING_AGENT_ROADMAP.md`.

| ID | Increment | Owner | Risk |
|---|---|---|---|
| EFB-000-BASELINE | Repository/source/build baseline | ARCHITECT | R1 |
| EFB-001-RULES-ADR | Rules contract and P0 decisions | ARCHITECT | R1 |
| EFB-002-BOARD-CORE | Deterministic headless board | BUILDER | R1 |
| EFB-003-TURN-COMBAT | Turn phases, player/enemy combat, intents | BUILDER | R1 |
| EFB-004-CARD-EFFECTS | Typed data-driven card/effect engine | BUILDER | R1 |
| EFB-005-CLASSES-STARTER | Three classes and starter decks | DOMAIN_SPECIALIST | R1 |
| EFB-006-ENEMIES-BOSSES | Enemies, elites, phased bosses | DOMAIN_SPECIALIST | R1 |
| EFB-007-RUN-PROGRESSION | Map, rewards, shop, bonfire, events, covenants | BUILDER | R1 |
| EFB-008-SHADE-NARRATIVE | Death, Shade, Remnants, endings | DOMAIN_SPECIALIST | R1 |
| EFB-009-ANDROID-CLIENT | Android app, portrait combat, touch UX | BUILDER | R1 |
| EFB-010-ART-ASSET-PIPELINE | Asset manifest, integration, atlases, VFX | DOMAIN_SPECIALIST | R1 |
| EFB-011-AUDIO-HAPTICS | Adaptive music, SFX, lighting response, haptics | DOMAIN_SPECIALIST | R1 |
| EFB-012-SAVE-REPLAY | Versioned persistence and deterministic replay | BUILDER | R2 |
| EFB-013-BALANCE-LAB | Bots, sweeps, balance metrics, exploit tests | DOMAIN_SPECIALIST | R1 |
| EFB-014-FULL-CONTENT | Launch content catalogue | DOMAIN_SPECIALIST | R1 |
| EFB-015-BACKEND | Optional daily seeds, leaderboard, replay validation | BUILDER | R2 |
| EFB-016-QA-RELEASE | Device, performance, accessibility and launch QA | ARCHITECT | R2 |

## Per-task Maestro loop

1. Inspect current repo state and source; start with `maestro coding-start EMBERFALL <TASK_ID> "<title>" "<outcome>" --workspace . --risk R1` (use R2 where register specifies R2).
2. Complete Grounding with explicit sources, constraints, dependencies, risks, acceptance and one bounded next increment.
3. `maestro begin-momentum <WORKFLOW_ID> --revision <N>`, then `maestro coding-seed-work ...`; refine generic work items into small, testable research/build/gate units.
4. Build in an isolated branch/worktree, record base/candidate SHA and changed paths.
5. Run project-specific GateRunner checks; never use Maestro's Python preset as a substitute for Gradle checks.
6. Deliver Momentum packet and submit exact candidate to Mastery.
7. Independent reviewer identity must differ from Builder; candidate reference must match exact candidate SHA; required checks must have VERIFIED evidence.
8. Complete Repetition and persist lessons, state updates, evidence and next increment.

The current upstream Maestro engine records candidate/builder identity and compares reviewer actor strings. That is a workflow guard, not cryptographic identity verification. Record model/vendor/agent identity and use genuinely distinct review execution. Do not self-review or use Echo as an implementation reviewer.

The upstream engine persists state and specialist dispatch packets; it does not start agents by itself. An adapter must be configured and observable before a task is claimed assigned/running.
