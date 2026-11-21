# AGENTS.md — EMBERFALL Repository Instructions

This repository is the engineering handoff for EMBERFALL. Read `README.md` first, then the complete `engineering/CODING_AGENT_ROADMAP.md` and `engineering/MASTER_CODING_AGENT_PROMPT.md` before changing files.

## Project truth

- The product source is `references/EMBERFALL_GAME_SPEC_v0.2.txt`, a normalized text copy of the provided v0.2 source. If the original source attachment is available, compare it before changing rules.
- `engineering/OPEN_DECISIONS.md` contains blocking gameplay ambiguities. Never invent a final rule for an open P0 item.
- `engineering/IMPLEMENTATION_WBS.json` is the 17-epic register; detailed acceptance criteria and implementation sequence live in the roadmap.
- Concept images, if supplied separately, are style references only. Do not treat in-image text or generated UI labels as game rules.
- This branch is a design/engineering handoff. It does not yet contain a playable game implementation.

## Required working order

1. Inspect the actual repository, git status/remotes/branch, available prototype, SDK/toolchain and asset provenance.
2. Establish a clean, reproducible build and baseline commit before gameplay changes.
3. Bootstrap and verify Maestro v0.3.0 using `maestro/bootstrap_current_maestro.sh`; pin the source commit listed in `maestro/CURRENT_MAESTRO_V0.3_PROCESS.md`.
4. Use one Maestro workflow per bounded increment: GROUNDING → MOMENTUM → MASTERY → REPETITION.
5. Keep simulation deterministic and Android-independent. Rendering is a projection of state, not the source of game rules.
6. Work in isolated branches/worktrees. Record base SHA, candidate SHA, changed paths, checks, known risks and evidence.
7. Run project-specific GateRunner checks. Do not use the Python preset as a proxy for Gradle/Android tests.
8. Submit the exact candidate to an independent reviewer with a different actor/model identity. Never self-review.
9. Complete Repetition after accepted Mastery; preserve lessons and next decision.

## Non-negotiable gameplay identity

Deck hue distribution → board spawn → connected group pop → colored Ink + chip → card play → board transformation → Wall pressure + telegraphed enemy intent. Keep this as one strategic loop. Do not turn the board into a decorative minigame.

## Engineering rules

- Prefer typed domain models, immutable transitions, deterministic RNG and data-driven content.
- All invalid actions must fail atomically.
- Cards/relics/enemies must have schemas and golden tests.
- Effect resolution must have deterministic order and explicit recursion/loop limits.
- Save after every node; replay binds seed, rules/content/RNG versions and input log.
- Use glyphs as well as hue color; respect portrait/mobile reach, scalable text, reduced motion and haptics/audio controls.
- Preserve asset source/artist/license metadata. Do not ship generated concept boards as final UI or final sprites.
- Do not add backend or large rendering dependencies before local-first core gameplay is proven.
- Do not merge, publish, deploy, spend or make destructive changes without explicit authorization.

## Reporting

Separate VERIFIED, OBSERVED, INFERRED, ASSUMED, PLANNED and BLOCKED. A plan is not implementation; a mockup is not an in-game asset; a command that was not run is not a passing test. Always report the exact current candidate SHA and remaining blockers.
