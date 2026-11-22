# EMBERFALL — Art and UI Baseline Lock

**Status:** Canonical visual baseline for the Android graphics reconstruction.  
**Version:** 0.4  
**Master image:** `reference/approved_base_visual_identity.png` (design-source only; not an Android runtime drawable)  
**Source:** the user-selected `a_cinematic_design_brief_infographic_art_bible_p.png` (1536 × 1024).  
**SHA-256:** `2d12913ff593a528473d50c80f252a05598f40c959e9c8e9400cf537614ed476` (the packaged copy matches byte-for-byte).

## 1. What is being reconstructed

The selected image is the approved visual identity and full game-screen outlook. It is not a generic mood prompt and must not be replaced by later AI-generated artboards. Reconstruct it as separate Android UI components and art layers:

- torchlit Cairn / gothic crypt, stained-glass rose, carved runes, braziers, memory fragments and antique gold / iron framing;
- dark slate surfaces with fine double rules, clipped corners, restrained ornaments and readable bone-colored text;
- five jewel-like playable hues—Ember, Tide, Moss, Gold and Violet—with distinct glyphs;
- neutral blocks Ash, Stone and Prism, each visually distinct;
- portrait Android experience, with the corridor / enemy / two intents above, the 7 × 7 glass board in the middle, and resources / card hand / controls below;
- ten core screens listed in Section 4.

Do not use the whole poster as a background texture or as a clickable screenshot. It is the measuring reference. Screen compositions and reusable vector/image layers are reconstructed independently in `ui/` and `art/`.

## 2. Canonical playable-class lock

There are exactly three playable classes in this baseline and their names/order are immutable unless the product owner approves a new specification:

| Order | Class | Hues | Play identity | Signature relic / mechanic from source |
|---|---|---|---|---|
| 1 | **Ashen Monk** | Ember / Moss | Burns Vigil for power; big single hits | Prayer Beads: first Tap each turn pays +2 Ink |
| 2 | **Quarry Witch** | Tide / Violet | Repaints the board and builds Spectrum Runs | Chisel Wand: once per fight, recolor one block free |
| 3 | **The Unlit** | Gold / Ash | Turns enemy Ash into fuel; thin, fast decks | Hollow Crown: each Ash block popped pays 1 Spark |

The Unlit uses a new painterly Gold/Ash portrait candidate with its generated source and crop script retained; the earlier vector concept is archived as fallback only.

The Ashen Monk is not optional and must never be dropped from the class-select, player collection, class definitions or marketing art. Quarry Witch and The Unlit are not to be renamed or replaced by generic fantasy archetypes.

**The Shade is not a fourth class.** It is the player's previous dead deck represented as an encounter in the next run. It belongs on the Shade encounter / afterlife screen only.

## 3. Canonical enemy and boss lock

- Act I boss: **The Hollow Warden** (source spec). Do not substitute “Warden” as a player class.
- Core example enemies include Cinder Husk, Ash Caller, Stone Herald and Memory Leecher. Preserve source names/roles unless changed by a signed content decision.
- Bosses remain separate from player classes. Each boss has a phased encounter treatment.

## 4. Ten screens from the approved outlook

1. Title & Main Menu
2. Class Select
3. Run Map (Act)
4. Combat (core loop)
5. Card Detail
6. Relic Gallery
7. Event
8. Bonfire
9. Shade (your dead deck)
10. Boss (phased)

The package supplies one 390 × 844 dp SVG composition per screen, independent layer/component masters and 3× preview PNGs. These are a reconstructed art/layout baseline, not finished interactive Android screens.

## 5. Palette and material lock

The master palette in `ASSET_MANIFEST.json` is canonical for the present reconstruction:

- Ink `#11131C`, Ink 2 `#191C29`, Ink 3 `#252A39`
- Slate `#3D4657`, Stone `#6E6B70`, Ash `#AAA7AC`, Bone `#E6DCC8`
- Antique Gold `#B98C4C`, Gold Highlight `#E3BC74`
- Ember `#D95C32`, Tide `#3F90BA`, Moss `#6F9857`, Gold hue `#D8AD42`, Violet `#8266B8`

Materials: blackened iron, worn slate, aged vellum, cut glass, candle wax, weathered leather and selective gold leaf. Gold is an accent, not a blanket fill. Glows communicate gameplay and should never obscure numbers, intent labels or touch targets.

## 6. Explicit anti-drift rules

The following invented class identities are **not** Emberfall classes and must not appear in the class lineup: Vanguard, Arcanist, Warden, Bladebound, Bladeborn, Veilcaller, Inkweaver, Shade (as class), Monk (as a new/renamed class). “Hollow Warden” remains a canonical boss name and is allowed in boss/enemy content.

Do not change:
- Emberfall to another project title;
- the five hue names / meanings;
- the three class names or their hue pairings;
- Shade from encounter to playable hero;
- the board into a swap-match-3 grid or a falling-block game;
- the portrait stacked layout into landscape UI;
- the visual baseline to generic neon cyberpunk, high-saturation mobile casino UI or a flat cartoon style.

## 7. Asset maturity and approval

- Approved as **design reference**: the master artboard.
- Candidate concept art: Ashen Monk portrait, Quarry Witch painterly portrait, The Unlit painterly portrait, Act backgrounds, enemy/boss art, card illustrations, relic icons and VFX.
- Runtime-ready as independent graphic sources subject to engineering conversion: SVG panel/button/board/intent/card/tile layers, Android palette/dimension XML and screen composition references.
- Still required: final character consistency pass, production-resolution environmental plates, animation sets, art-direction approval, provenance/IP review, Android visual regression and device performance testing.

Never call the entire art pack “production-ready” until those gates pass. `VALIDATION_REPORT.json` records static validation only; it does not claim a built Android app or physical-device acceptance.
