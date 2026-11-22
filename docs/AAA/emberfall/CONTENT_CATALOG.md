# EMBERFALL — Content Catalog & Source Fidelity Status

## 1. Canonical Playable Classes (Immutable Order)
1. **Ashen Monk** (`Ember / Moss`) — Signature Relic: `Prayer Beads` (First Tap each turn grants `+2 Ink`). Burns Vigil for heavy single-hit attacks.
2. **Quarry Witch** (`Tide / Violet`) — Signature Relic: `Chisel Wand` (Once per fight, freely recolour one unsealed tile). Repaints board, breaks Stone, manipulates Spectrum & cleanses Seals.
3. **The Unlit** (`Gold / Ash`) — Signature Relic: `Hollow Crown` (`+1 Spark` per Ash block popped). Thin Ash/Gold starter, eligible for `Unlit Covenant` (`ADR-017`).

## 2. Starter Decks
- **The Unlit (`ADR-014`, 12 Gold-frame cards — Canonical Composition / Provisional Base Stats):**
  - `4x` **Gold Strike** (`unlit_gold_strike`, Gold frame, 1 Gold Ink, 7 dmg)
  - `4x` **Ash Ward** (`unlit_ash_ward`, Gold frame, 1 Generic, 6 Guard + 1 Ash)
  - `2x` **Pop Shot** (`unlit_pop_shot`, Gold frame, 1 Gold Ink, 6 dmg + Draw 1)
  - `1x` **Sift** (`unlit_sift`, Gold frame, 1 Generic, 2 Ash + 1 Spark + Draw 1)
  - `1x` **Hollow Crown** (`unlit_hollow_crown`, Gold frame, 1 Gold Ink, 5 Guard + 1 Spark + 3 Ash)
- **Ashen Monk (`10` cards — `[PROVISIONAL awaiting Design Bible v0.2]`):**
  - `3x Ember Palm`, `3x Moss Stance`, `2x Bead Strike` (Vigil burn burst), `1x Quiet Root`, `1x Cinder Sutra`
- **Quarry Witch (`10` cards — `[PROVISIONAL awaiting Design Bible v0.2]`):**
  - `3x Tide Vein`, `3x Violet Shroud`, `2x Slate Bolt`, `1x Well's Echo`, `1x Quarry Purge`

## 3. Canonical Neutral Additions
- **Gold Eye (`neutral_gold_eye`):** `[ADR-011]` Dispels active board Fog (`clearsFog = true`), restoring hue colors & Ink visibility, grants 4 Guard, draws 1 card.

## 4. Act Structure & Route Composition (`ADR-018`)
Each 15-row Act map enforces across all traversable routes:
- Combats (`4..8`, target ~6), Elites (`1..2`), Campfires (`2`), Merchant (`1`), Events (`1..3`, target ~2), Treasure (`1`), Boss (`1` at Row 15; `Hollow Warden` in Act I), plus **The Shade** at Act I Row 5 (`ADR-016`).
- Enemy HP Scaling (`ADR-013`): `round(36 * 1.11^node)` (`x1.6` Elite, `x3.0` Boss).
