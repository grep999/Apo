# AGENTS.md — EMBERFALL Repository Instructions

This repository is the engineering handoff for EMBERFALL. Read `README.md`, then `engineering/CODING_AGENT_ROADMAP.md` and `engineering/MASTER_CODING_AGENT_PROMPT.md` before changing files. For any UI/asset work, also read `graphics/README.md`, `graphics/ART_BASELINE_LOCK.md` and `graphics/ANDROID_ART_INTEGRATION_HANDOFF.md`.

## Project truth

- Product source: `references/EMBERFALL_GAME_SPEC_v0.2.txt`, normalized text copy of the provided v0.2 source.
- `engineering/OPEN_DECISIONS.md` contains blocking gameplay ambiguities. Never invent a final rule for an open P0 item.
- `engineering/IMPLEMENTATION_WBS.json` is the 17-epic register; detailed acceptance criteria are in the roadmap.
- `graphics/ASSET_MANIFEST_SUMMARY.json` locks the selected UI base image and delivered graphics package hashes.
- Companion binary package files must be extracted into the actual app repository before integration; the documentation branch alone does not contain all PNG/JPG/WebP binaries.
- This branch is a design/engineering handoff. It does not yet contain a playable game implementation.

## Canonical visual/game identity — no substitutions

Exactly three playable classes, in this order:
1. **Ashen Monk** — Ember / Moss.
2. **Quarry Witch** — Tide / Violet.
3. **The Unlit** — Gold / Ash.

Ashen Monk is mandatory and must remain first. Shade is the previous dead deck as an encounter, never a playable class. Act I boss is **The Hollow Warden**.

Preserve the user-selected 1536×1024 design reference identified in `graphics/ASSET_MANIFEST_SUMMARY.json`. Rebuild its ten screens from independent layers and native widgets. Do not ship the flattened reference as a runtime background or clickable screenshot. Do not introduce Vanguard, Arcanist, Warden, Bladeborn, Bladebound, Veilcaller or Inkweaver as substitute player classes.

## Required working order

1. Inspect actual repository/source, Git state, prototype, SDK/toolchain and asset provenance.
2. Establish a clean reproducible build before gameplay changes.
3. Bootstrap and verify pinned Maestro v0.3.0 per `maestro/CURRENT_MAESTRO_V0.3_PROCESS.md`.
4. Use one workflow per bounded increment: GROUNDING → MOMENTUM → MASTERY → REPETITION.
5. Keep simulation deterministic and Android-independent. Rendering is a projection of state, not the source of game rules.
6. Work in isolated branches/worktrees. Record base SHA, candidate SHA, changed paths, checks, risks and evidence.
7. Run real project-specific GateRunner checks. Do not use Python checks as a proxy for Gradle/Android.
8. Submit exact candidates to an independent reviewer with a different actor/model identity. Never self-review.
9. Complete Repetition after accepted Mastery; preserve lessons and next decision.

## Engineering rules

- Prefer typed domain models, immutable transitions, deterministic RNG and data-driven content.
- Invalid actions fail atomically.
- Cards/relics/enemies have schemas and golden tests.
- Effect resolution has deterministic order and explicit recursion limits.
- Save after every node; replay binds seed, rules/content/RNG versions and input log.
- Use glyphs as well as hue color; respect portrait reach, scalable text, reduced motion and audio/haptic controls.
- Preserve asset source/artist/license metadata. Concept art is not final approved production art.
- Do not ship generated concept boards as UI screens; use the provided independent SVG/component/layer assets.
- Do not add backend or large rendering dependencies before the local-first core is proven.
- Do not merge, publish, deploy, spend or make destructive changes without explicit authorization.

## Reporting

Separate VERIFIED, OBSERVED, INFERRED, ASSUMED, PLANNED and BLOCKED. A plan is not implementation; a mockup is not an in-game asset; a command that was not run is not a passing test. Always report candidate SHA and remaining blockers.
