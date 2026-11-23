# EMBERFALL — Coding Agent Handoff v1.0

This branch is the shared engineering handoff for EMBERFALL, a premium, portrait-first Android roguelike puzzle deckbuilder.

## Start here

1. [Coding agent production roadmap](engineering/CODING_AGENT_ROADMAP.md) — architecture, implementation sequence, milestones, gates, QA and release.
2. [Master coding-agent prompt](engineering/MASTER_CODING_AGENT_PROMPT.md) — principal engineer persona and working contract.
3. [Open decisions](engineering/OPEN_DECISIONS.md) — P0 gameplay questions that block dependent implementation.
4. [Architecture decisions](engineering/ARCHITECTURE_DECISIONS.md) — accepted constraints and proposed ADRs.
5. [Implementation WBS](engineering/IMPLEMENTATION_WBS.json) and [CSV](engineering/IMPLEMENTATION_WBS.csv) — 17 epic-level work packages.
6. [Maestro v0.3 operating guide](maestro/CURRENT_MAESTRO_V0.3_PROCESS.md), [task register](maestro/TASK_REGISTER.md), [17 engineering Task v2 packets](maestro/EMBERFALL_TASKS_V2.json), and [cycle packet templates](maestro/CORE_PACKET_TEMPLATES_V03.json).
7. [Graphics v0.4 agent handoff](graphics/README.md), [exact visual baseline lock](graphics/ART_BASELINE_LOCK.md), [Android art integration contract](graphics/ANDROID_ART_INTEGRATION_HANDOFF.md), and [graphics Maestro Task v2](graphics/MAESTRO_TASK_EFB_ART_UI_BASELINE_V2.json).
8. [Graphics pack manifest summary](graphics/ASSET_MANIFEST_SUMMARY.json) — exact base-image and companion archive hashes.
9. [Original specification — normalized text copy](references/EMBERFALL_GAME_SPEC_v0.2.txt) — structured text transcription of the supplied v0.2 game specification. Compare with the original source attachment in the local full handoff ZIP if exact wording matters.

## Repository and workflow baseline

- Remote: https://github.com/grep999/Apo
- Active handoff branch: `handoff/emberfall-coding-agent-v1.0`
- The default branch contains only the repository bootstrap README.
- Current Maestro source lock: `grep999/maestro` v0.3.0, observed `main` commit `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.
- Root `AGENTS.md`, `CLAUDE.md` and `.github/copilot-instructions.md` provide agent-specific entry points.
- No playable game implementation is claimed by this handoff. The initial work is repository/source discovery, reproducible build setup and P0 rules grounding.
- Graphics v0.4 documents and Task v2 are mirrored here. The 30MB full art-source ZIP and 2.6MB Android resource-only ZIP are companion binary deliverables; their SHA-256 hashes are in `graphics/ASSET_MANIFEST_SUMMARY.json`. Download/extract the companion bundle into the actual game source workspace before integration. They are not embedded as binary Git blobs in this documentation branch.

## Rules for all agents

- Treat the supplied v0.2 game specification as authoritative. Never infer gameplay rules from generated image text.
- Do not implement a P0 ambiguity without an explicit decision or a separately labelled prototype experiment.
- Keep the game simulation deterministic and Android-independent.
- Work in isolated branches/worktrees; bind every candidate to base SHA, candidate SHA and changed paths.
- Follow Maestro: GROUNDING → MOMENTUM → MASTERY → REPETITION.
- A Builder cannot review its own candidate. PASS requires actual evidence and required verified gates.
- Never represent plans, sample outputs or unrun commands as completed implementation.
- Do not merge, publish, spend, deploy or perform destructive actions without explicit human authorization.

## Access

This repository is private. Every human or service agent that needs to collaborate must be granted access through GitHub repository access controls. A branch being present does not automatically grant access to agents whose GitHub identity lacks repository permission.

## Scope and provenance

Generated artboards are concept references, not final licensed sprites, card illustrations or UI assets. The selected base image is the canonical visual design authority, copied byte-for-byte and hash-locked in graphics v0.4. Preserve source/artist/license metadata for every production asset. Final app integration and device verification are still required.


## Current implementation baseline

- **EFB-002 board core:** Product Owner accepted at exact SHA `6b6e139890a205ead337a94226464a0b5ddd82da`; the single requested feedback round is complete and further EFB-002 review was waived by the owner. Exact-head Board Core, Maestro GateRunner and ADR validation passed.
- **EFB-003 turn/combat:** clean-room implementation candidate is in `feat/efb003-turn-combat-clean`. It builds directly on `src/main/kotlin/com/emberfall/board/BoardCore.kt` and is tracked in PR #15.
- **Legacy exclusion:** the old prototype and its assets are explicitly excluded by `engineering/PRODUCT_OWNER_DECISION_CLEAN_CORE.md`. They are not build inputs, product source, design authority or permitted dependencies.
- The new exact-SHA EFB-003 workflow runs the full Kotlin/JVM test suite and checks the Android-independent domain boundary. No release or merge approval is implied.
