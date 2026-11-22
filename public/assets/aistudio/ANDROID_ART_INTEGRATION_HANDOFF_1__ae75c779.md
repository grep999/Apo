# EMBERFALL — Android Art Reconstruction & Integration Handoff v0.4

## 0. This is the exact approved source

- User-selected master image: `reference/approved_base_visual_identity.png`
- Dimensions: 1536 × 1024 px
- SHA-256: `2d12913ff593a528473d50c80f252a05598f40c959e9c8e9400cf537614ed476`
- This copy is byte-for-byte identical to the supplied source image.

The master image is an art-direction and measurement reference. It is **not** a screen texture, clickable screenshot, or full-screen runtime bitmap. The game UI is reconstructed as separate SVG compositions, independently reusable components/layers, source art, compressed Android drawable resources and native Compose integration samples.

## 1. Canonical EMBERFALL identity (must not drift)

The three playable classes are fixed and displayed in this order:

1. **Ashen Monk** — Ember / Moss. Burns Vigil for power; big single hits. Signature: Prayer Beads.
2. **Quarry Witch** — Tide / Violet. Repaints the board and builds Spectrum Runs. Signature: Chisel Wand.
3. **The Unlit** — Gold / Ash. Turns enemy Ash into fuel. Signature: Hollow Crown.

The Ashen Monk has not been removed, renamed or demoted. He remains the first class and his existing painterly portrait is preserved. Quarry Witch retains the Tide/Violet portrait treatment. The Unlit now has a painterly pale/silver-haired Gold/Ash concept portrait candidate; the former authored vector portrait is retained only as an archived fallback. **Shade is not a fourth playable class**: it is the player's prior dead deck represented as an encounter. Act I's canonical boss remains **The Hollow Warden**.

## 2. What has been reconstructed from the image

Ten portrait screen compositions, each authored as an independent 390 × 844 SVG and rendered to a 1170 × 2532 (3×) visual-regression PNG:

| # | Screen | Runtime data source |
|---|---|---|
| 01 | Title & Main Menu | `TitleUiState` |
| 02 | Class Select | `ClassSelectUiState` |
| 03 | Run Map (Act) | `RunMapUiState` |
| 04 | Combat / Glass Board | `CombatUiState` |
| 05 | Card Detail | `CardDetailUiState` |
| 06 | Relic Gallery | `RelicGalleryUiState` |
| 07 | Event Encounter | `EventUiState` |
| 08 | Bonfire / Rest | `BonfireUiState` |
| 09 | Shade Encounter | `ShadeEncounterUiState` |
| 10 | Boss (Hollow Warden) | `BossCombatUiState` |

The files are in `ui/screens/`. Do not merge them into one raster. Use their layout, ornament, color, proportions and hierarchy as the Android implementation reference.

## 3. Reusable graphic layer library

- `ui/components/`: ornate dark-slate panels, primary/secondary buttons, board bezel, hand tray, Vigil bar and intent plaques.
- `ui/layers/`: corridor arch, card shell, left/right intent plaques and eight hue/neutral glass tile masters.
- `art/characters/`: Ashen Monk, Quarry Witch, The Unlit, Sister Verrin and Shade encounter art.
- `art/environments/`: Act I Fallen Crypt, Act II Drowned Halls, Act III Luminous Spire.
- `art/tiles/`: Ember, Tide, Moss, Gold, Violet, Ash, Stone, Prism.
- `art/cards/`: initial named card illustration candidates.
- `art/relics/`, `art/enemies/`, `art/icons/`, `art/vfx/`, `art/brand/`: game asset families.
- `android/app/src/main/res/`: Android starter palette/dimensions, button backgrounds and compressed WebP runtime candidates.
- `android/ASSET_RESOURCES.csv`: Android resource name → source file → dimensions/bytes map.
- `ASSET_MANIFEST.json`: SHA-256 inventory and maturity status.

## 4. Android import

1. Copy the contents of `android/app/src/main/res/` into the target game's `app/src/main/res/`; merge rather than overwrite existing resource directories.
2. Check duplicate resource names. The `ef_*` prefix is intentional to minimize collisions.
3. Copy `android/sample/EmberfallArtResources.kt`, `EmberfallClassSelectScreen.kt`, `EmberfallOrnateComponents.kt` and `EmberfallCombatScreen.kt` into an appropriate UI package, then adapt package names, navigation and app-specific dependencies.
4. Keep SVG masters and source PNG/JPG artwork in the game's art/source tree. The Android WebP files are generated delivery resources; do not edit them by hand.
5. Re-run `python tools/export_android_resources.py` after source art changes and `python tools/render_previews.py` after layout changes.
6. Do **not** copy `reference/approved_base_visual_identity.png` into Android resources. It stays in `reference/` only.

The drawable resources use names such as `ef_class_ashen_monk`, `ef_class_quarry_witch`, `ef_class_the_unlit`, `ef_tile_ember`, `ef_scene_act1_fallen_crypt` and `ef_enemy_hollow_warden`.

## 5. Runtime implementation requirements

- Android owns layout, navigation, touch semantics, animations, accessibility and live text.
- Game core owns legality, board resolution, Ink, Spectrum Run, card costs/effects, enemy intents, damage, Wall/overflow and save state.
- Combat must render 49 visible board cells from typed `GameState` data (7 × 7). The incoming Wall row is separately modeled according to the resolved game rule.
- Render the next two enemy intents from the combat engine's prediction; do not infer or locally simulate them in Compose.
- Render the player's five Ink pools, Spark, Taps, Spectrum Run, Wall rows, Vigil, Block and Flasks from current state. Never hard-code fixture values from the SVG.
- Cards are live components: frame/illustration can be image resources; cost/name/type/rules text/upgrade state are runtime values.
- A tile must display a unique glyph as well as its hue. Ash, Stone and Prism are distinct neutral IDs, not hue aliases.
- `ClassSelectUiState.classes` must be `[ashen_monk, quarry_witch, the_unlit]`. Do not add Shade as a class.
- Keep touch targets ≥48 dp, preserve one-handed portrait reach, scale text and support reduced motion/flashing and opt-out haptics.

## 6. Visual language to preserve

- Ink/dark-slate surfaces, blackened iron and worn carved stone.
- Double-rule panels, clipped-corner details and restrained antique-gold trim.
- Bone-colored readable text; title typography is ornamental while live values use a highly legible UI face.
- Jewel-glass tiles with color + shape glyph, not flat color squares.
- Warm ember/torch upper corridor, glass board as the center focal object, lower-third hand and controls.
- Act palette evolution: warm Fallen Crypt, cold Drowned Halls, luminous Spire.
- Gold is an accent and edge highlight, not a blanket fill. Glow must not obscure card text, intent values or touch areas.

## 7. Acceptance gate for coding agent

### Static asset gate
- [ ] `python tools/validate_graphics.py` passes with no SVG parse errors or unresolved relative image references.
- [ ] Ten screen SVGs and ten 3× previews exist.
- [ ] All three class portraits are present, correctly mapped and in canonical order.
- [ ] Android resource CSV entries resolve to actual WebP/XML files; no full poster resource exists.
- [ ] Asset manifest contains SHA-256, dimensions, provenance and maturity status.

### Android integration gate
- [ ] Gradle build, lint and tests pass in the actual EMBERFALL app repository.
- [ ] Class Select uses all three intended class IDs and data-driven descriptions.
- [ ] Combat uses 49 live cells, live cards/resources and exactly the intent preview emitted by the engine.
- [ ] Compare device screenshots at 360 × 800, 390 × 844 and 412 × 915 against the SVG previews and master artboard.
- [ ] Large font scale, screen-reader labels, focus order, grayscale and reduced-motion modes pass.
- [ ] Touch inputs and scrolling remain responsive; 60 fps target is measured on a target mid-range Android device.

### Governance gate
- [ ] Start `EFB-ART-UI-BASELINE-01` via the current Maestro v0.3.0 workflow in the actual app repository.
- [ ] Grounding records the exact source image SHA, graphics ZIP SHA, app base SHA and the class identity lock.
- [ ] Momentum contains candidate SHA, changed paths and GateRunner evidence for actual Gradle commands.
- [ ] Independent Mastery reviewer is distinct from the builder and reviews the exact candidate.
- [ ] Repetition records findings, accepted deviations, visual deltas and next art increment.

No Android app build or physical-device pass is claimed by this asset pack. Static graphics validation is separate from native app acceptance.
