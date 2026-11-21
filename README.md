# EMBERFALL — Coding Agent Handoff v1.0

This branch is the shared engineering handoff for EMBERFALL, a premium, portrait-first Android roguelike puzzle deckbuilder.

## Start here

1. [Coding agent production roadmap](engineering/CODING_AGENT_ROADMAP.md) — architecture, implementation sequence, milestones, gates, QA and release.
2. [Master coding-agent prompt](engineering/MASTER_CODING_AGENT_PROMPT.md) — principal engineer persona and working contract.
3. [Open decisions](engineering/OPEN_DECISIONS.md) — P0 gameplay questions that block dependent implementation.
4. [Architecture decisions](engineering/ARCHITECTURE_DECISIONS.md) — accepted constraints and proposed ADRs.
5. [Implementation WBS](engineering/IMPLEMENTATION_WBS.json) and [CSV](engineering/IMPLEMENTATION_WBS.csv) — 17 epic-level work packages.
6. [Maestro v0.3 operating guide](maestro/CURRENT_MAESTRO_V0.3_PROCESS.md), [task register](maestro/TASK_REGISTER.md), [17 Maestro Task v2 packets](maestro/EMBERFALL_TASKS_V2.json), and [cycle packet templates](maestro/CORE_PACKET_TEMPLATES_V03.json).
7. [Original specification — normalized text copy](references/EMBERFALL_GAME_SPEC_v0.2.txt) — structured text transcription of the supplied v0.2 game specification. Compare with the original source attachment in the local full handoff ZIP if exact wording matters.

## Repository and workflow baseline

- Remote: https://github.com/grep999/Apo
- Active handoff branch: `handoff/emberfall-coding-agent-v1.0`
- The default branch contains only the repository bootstrap README.
- Current Maestro source lock: `grep999/maestro` v0.3.0, observed `main` commit `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.
- Root `AGENTS.md`, `CLAUDE.md` and `.github/copilot-instructions.md` provide agent-specific entry points.
- No game implementation is claimed by this handoff. The initial work is repository/source discovery, reproducible build setup and P0 rules grounding.
- The complete local handoff ZIP additionally contains the detailed 77-child-task WBS, 51 per-cycle packet templates, design-volume maps, original source attachment and binary concept-art references. Those extended documents/images are not all mirrored into this remote branch; the remote branch contains the implementation roadmap and epic-level task register required to start.

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

Generated artboards are concept references, not final licensed sprites, card illustrations or UI assets. Preserve source/artist/license metadata for every production asset. The art direction's torch-lit crypt, carved-glass board, five-hue stained glass and ornate iron/gold interface are a design north star; final production assets still require proper creation, review, optimization and provenance.
