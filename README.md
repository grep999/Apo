# EMBERFALL — Coding Agent Handoff v1.0

This branch is the shared engineering handoff for EMBERFALL, a premium, portrait-first Android roguelike puzzle deckbuilder.

## Start here

1. [Coding agent production roadmap](engineering/CODING_AGENT_ROADMAP.md) — architecture, implementation sequence, milestones, gates, QA and release.
2. [Master coding-agent prompt](engineering/MASTER_CODING_AGENT_PROMPT.md) — principal engineer persona and working contract.
3. [Open decisions](engineering/OPEN_DECISIONS.md) — P0 gameplay questions that block dependent implementation.
4. [Architecture decisions](engineering/ARCHITECTURE_DECISIONS.md) — accepted constraints and proposed ADRs.
5. [Implementation WBS](engineering/IMPLEMENTATION_WBS.json) and [CSV](engineering/IMPLEMENTATION_WBS.csv) — 17 epics and 77 child tasks.
6. [Maestro v0.3 operating guide](maestro/CURRENT_MAESTRO_V0.3_PROCESS.md) — current workflow lifecycle and evidence policy.
7. [Maestro task packets](maestro/tasks_v2/) and [cycle packet templates](maestro/packets_v03/) — Grounding/Momentum/Mastery/Repetition work inputs.
8. [Original game specification](references/EMBERFALL_GAME_SPEC_v0.2.txt) — authoritative game rules and content brief.
9. [Visual references](art/concept_references/) — mood/composition references only, not final approved production assets.

## Repository and workflow baseline

- Remote: https://github.com/grep999/Apo
- Active handoff branch: `handoff/emberfall-coding-agent-v1.0`
- The default branch contains only the repository bootstrap README.
- Current Maestro source lock: `grep999/maestro` v0.3.0, observed `main` commit `e5247d950fd48a8e1e07acec68ffd04d1445ba55`.
- No game implementation is claimed by this handoff. The initial work is repository/source discovery, reproducible build setup and P0 rules grounding.

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

The prior visual-design and V00 design chapters remain reference material. Generated artboards are concept references, not final licensed sprites, card illustrations or UI assets. Preserve source/artist/license metadata for every production asset.
