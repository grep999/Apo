# EMBERFALL — Test Strategy & Verification Gates

## 1. Test Layers
1. **Domain Unit & Contract Tests (`EmberfallEngineTest.kt`)**
   - Strictly validates spec contracts (NOT buggy implementations):
     - P0.1: Monk Vigil burn (`vigilCost`, `vigilBurnMax`) + NO repeated multi-hit counterattack exploit
     - P0.2: Branded blocks (`2 Vigil` per block), Fog (`3 Taps` concealment + `Gold Eye` clear), Seal (`3 turns` column lock vs tap/gravity/paint/Wall)
     - P0.3: Wall overflow (full top row -> `-8 HP`, burn rows `0..1`, `-1 Flask`, skip rise, no Guard absorption, partial top row safety, recovery on next turn, `ZERO_FLASKS`)
     - P0.4: Echo matrix repeating ONLY primary effect once without Echo-of-Echo
     - P0.5: Dread 3-turn Ash-only no-card route vs Hearthkeeper empty-air & every disqualifying reset
     - P0.6: Adversarial transition-guard rejection across all `GameCommand`s
     - P1: Unlit ADR-014 12-card counts, Spectrum Run 0 / wrap / Prism / out-of-order reset, ADR-013 `round(36*1.11^node)` scaling, 15-row map Event route validator, full Shade deck intent transformation, deterministic BoardPaintDeck no-move recovery
2. **Headless Multi-Seed Simulation (`SimulationRunner`)**
   - Multi-seed full 3-act run verification across `ASHEN_MONK`, `QUARRY_WITCH`, and `THE_UNLIT` reporting Act III reach, win/loss splits, and zero softlocks.
3. **Persistence, Codec & Robolectric UI (`ExampleRobolectricTest.kt`)**
   - `RunSnapshotCodec` unknown tile rejection, `v2 -> v3` migration, round-trip of Brand/Fog/Seal/Empty/Dread/Event.
   - `EmberfallDatabase` non-destructive schema migration verification.
   - Serialized repository write ordering check.
   - Robolectric Compose CUJs: class portraits, Unlit-only Covenant lock, Row 1 map entry, one-tap block click, generic Ink payment dialog, Fog visual veil, Event node screen, defeat navigation to `RunSummary`.
