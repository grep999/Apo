# EMBERFALL Android UI File Locations — v0.4

Extracted package root:
`/mnt/data/EMBERFALL_ANDROID_GRAPHICS_v0.4/`

The same relative paths exist inside `EMBERFALL_ANDROID_GRAPHICS_v0.4.zip`.

## 1. Screen layout masters — SVG

Use these as 390 × 844 dp layout references. Build native Compose/View controls and live state bindings over them; do not ship these full compositions as static clickable screenshots.

`ui/screens/`

- `01_title_main_menu.svg`
- `02_class_select.svg`
- `03_run_map.svg`
- `04_combat_glass_board.svg`
- `05_card_detail.svg`
- `06_relic_gallery.svg`
- `07_event_encounter.svg`
- `08_bonfire_rest.svg`
- `09_shade_encounter.svg`
- `10_boss_hollow_warden.svg`

## 2. Reusable UI component masters — SVG

`ui/components/`

- `board_bezel.svg`
- `button_primary.svg`
- `button_secondary.svg`
- `hand_tray.svg`
- `hud_vigil_bar.svg`
- `intent_plaque.svg`
- `panel_ornate.svg`

## 3. Reusable art / UI layers — SVG

`ui/layers/`

- `card_shell_dynamic.svg`
- `corridor_arch_layer.svg`
- `glass_tile_ash.svg`
- `glass_tile_ember.svg`
- `glass_tile_gold.svg`
- `glass_tile_moss.svg`
- `glass_tile_prism.svg`
- `glass_tile_stone.svg`
- `glass_tile_tide.svg`
- `glass_tile_violet.svg`
- `intent_left_layer.svg`
- `intent_right_layer.svg`

## 4. Android runtime static imagery — WebP

`android/app/src/main/res/drawable-nodpi/`

Contains 78 WebP drawables: canonical class portraits, act environments, tiles, card illustrations, relics, enemies, UI glyphs, brand images and VFX.

Merge into the actual game app's:
`<GAME_REPOSITORY>/app/src/main/res/drawable-nodpi/`

Also merge:
- `android/app/src/main/res/values/colors.xml` → app `res/values/colors.xml`
- `android/app/src/main/res/values/dimens.xml` → app `res/values/dimens.xml`
- `android/app/src/main/res/drawable/*.xml` → app `res/drawable/`

Resolve duplicate resource names; do not blindly overwrite app resources.

## 5. Preview renders

`ui/previews/`

Contains 3× PNG previews for all ten screens, plus contact sheet, class roster and component/layer previews. These are QA references, not runtime screens.

## 6. Android sample integration code

`android/sample/`

- `EmberfallArtResources.kt`
- `EmberfallClassSelectScreen.kt`
- `EmberfallCombatScreen.kt`
- `EmberfallOrnateComponents.kt`
- `README.md`

Starter examples only; adapt package names and connect callbacks/data to the actual app.

## 7. Dynamic data boundary — mandatory

Static assets provide artwork, frames, tiles, buttons, borders and background imagery only. All changing values and text must come from game-state/content models:

- Vigil, Block, Flasks, Ink and Spark counts;
- Tap count, Spectrum Run and Wall state;
- enemy health and the next two intent labels/values;
- card name, cost, description, upgrade state and target legality;
- map nodes, rewards, Cinders, relic names/descriptions and event dialogue.

Do not hardcode mockup numbers or labels into SVG, WebP or screen composables. Game-core remains authoritative for rule outcomes; UI renders those values and dispatches user actions.

## 8. Quick path commands

From the package root:

```bash
find ui/screens -maxdepth 1 -type f -name '*.svg' | sort
find ui/components ui/layers -type f -name '*.svg' | sort
find android/app/src/main/res/drawable-nodpi -maxdepth 1 -type f -name '*.webp' | sort
find ui/previews -maxdepth 1 -type f -name '*.png' | sort
```
