# EMBERFALL Android Graphics v0.4 — Agent Handoff

This graphics handoff reconstructs the user's exact approved artboard into separate screen masters, reusable SVG layers, art-source files and Android resource starters.

## Start here

1. Read `ART_BASELINE_LOCK.md` — exact master image, class identity lock, anti-drift rules.
2. Read `ANDROID_ART_INTEGRATION_HANDOFF.md` — ten-screen integration contract, data binding, import procedure and acceptance gates.
3. Read `ASSET_MANIFEST_SUMMARY.json` — artifact counts and hashes.
4. Import the matching delivered bundle `EMBERFALL_ANDROID_GRAPHICS_v0.4.zip` (or only `EMBERFALL_ANDROID_RESOURCES_v0.4.zip` for the resource merge) into the actual game workspace. The ZIPs are delivered as companion files in the project handoff; they are not embedded as GitHub binary assets in this branch.
5. Start `EFB-ART-UI-BASELINE-01` through Maestro v0.3.0 in the actual Android app repository.

## Canonical class identities

| Order | ID | Class | Hues |
|---|---|---|---|
| 1 | `ashen_monk` | Ashen Monk | Ember / Moss |
| 2 | `quarry_witch` | Quarry Witch | Tide / Violet |
| 3 | `the_unlit` | The Unlit | Gold / Ash |

Ashen Monk is preserved and must not be removed. Shade is a dead-deck encounter, not a playable class. Act I boss is The Hollow Warden.

## What v0.4 contains

- 10 independent Android portrait screen SVG masters (390×844 dp).
- 10 3× screen preview PNGs.
- 7 reusable UI component SVGs and 12 independent UI layer SVGs.
- Painterly concept portraits for all three canonical classes.
- 78 Android WebP resource files plus resource XML and CSV mapping.
- Art sources for cards, relics, tiles, environments, enemies, VFX and brand assets.
- Compose samples for combat, class select, resource mapping and ornate UI components.
- Asset manifest, SHA-256 provenance, local browser and static validator.

## Non-negotiable implementation rules

- Do not ship the master poster as a screen image. Rebuild native interactive controls and live text from the independent layers.
- Do not infer game rules or content values from generated mockup text. The v0.2 text game spec remains authoritative.
- Runtime screen values and card rules must be bound to live GameState/content models.
- The entire graphics pack is not a finished application. No Gradle build, physical-device check or independent Maestro Mastery review is claimed yet.
- Preserve the exact approved master hash from `ASSET_MANIFEST_SUMMARY.json`.
