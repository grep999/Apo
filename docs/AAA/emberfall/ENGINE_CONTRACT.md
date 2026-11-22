# EMBERFALL — Engine Contract

**Module:** `app/src/main/java/com/example/engine/` (Pure Kotlin/JVM — 0 Android/Compose/Room dependencies)  
**Engine Version:** `3` | **Snapshot Schema Version:** `3` | **Content Version:** `3`

## 1. Turn State Machine (`ADR-001`)
- **Player Interactive Phases:** `TurnPhase.WEAVE` (up to 3 Swaps + up to 1 Ember Surge bonus move on 4+ match/cascade, `WitnessIllusion`, `TriggerOverdrive`, Chisel Wand, Flask, or `ProceedToCast`), `TurnPhase.CAST` (play Ink/Spark cards, `TriggerOverdrive`, Chisel Wand, Flask, `EndTurn`).
- **Atomic End-of-Turn Substeps inside `EndTurn`:** `WALL` (`riseIncomingRow` / ADR-008 Overflow) -> `ENEMY` (Slot I & II intents) -> `ADR-017` (Dread 3-turn check) -> `CLEANUP` (decrement `sealedColumns`, keep Vigil buffer capped, reset Spectrum unless Relic, discard/draw 5 cards -> next turn `WEAVE`).
- **Terminal Combat Phases:** `TurnPhase.COMBAT_WON`, `TurnPhase.GAME_OVER`. Mid-turn kills in `resolvePostAction` increment `stats.turnsTaken` by 1 so winning turns are accurately recorded.

## 2. Board Rules (`ADR-002` .. `ADR-008`, `ADR-010` .. `ADR-012`)
- **Dimensions:** 7×7 grid (`rows 0..6`, `cols 0..6`) plus 7-tile `incomingRow`. Initial seed fills `rows 2..6` (35 deck-painted tiles) leaving `rows 0..1` as `TileKind.Empty` headroom.
- **Authoritative Match-3 Swap Contract (`SwapCells`):**
  - Live player input (`CombatArenaScreen` / `EmberfallViewModel`) and automated simulation (`SimulationRunner`) strictly execute Match-3 `GameCommand.SwapCells` (adjacent tile drag, tap-tap, or `One-Tap Quick-Swap` when enabled in Settings).
  - Swapping two orthogonally adjacent unsealed non-Empty, non-Stone tiles succeeds when forming `>= 3` horizontal/vertical matching tiles (or when either tile is a `Prism`, triggering hue-cross / dual-Prism / adjacent-Ash detonation).
  - Gravity + `BoardPaintDeck` refill automatically resolves cascading 3+ lines until zero idle lines remain (`findLineMatches(board).isEmpty()`), followed by `ensurePlayableSwap` guaranteeing `hasAnyValidSwap(board) == true`.
  - Forged/seeded `Prism` tiles are special board payoffs and are excluded from `BoardPaintDeck.discardBag` recycling; when `drawBag` empties with fewer than 18 discarded tiles, a fresh ADR-003 weighted bag is drawn.
  - Legacy `GameCommand.TapCell` (`resolveGroup` / `ADR-004` connected-cluster rules) remains supported inside `GameEngine` for deterministic replay compatibility and legacy ADR regression tests.
- **Special Tiles & Hazards:**
  - `Prism`: wildcard in Match-3 lines; direct Prism swap detonates matching partner hue along row/col cross (`SwapCells`) or adopts single wheel-tie-broken contacting hue (`TapCell` / `ADR-004`).
  - `Ash`: 3+ Ash line (`SwapCells`) or connected `>= 2` Ash group (`TapCell`) clears directly (`ADR-006` / `ADR-017`), OR orthogonal adjacent Ash clears alongside any hue match. Yields `0` Ink, deals `max(0, n - 1)` chip damage after Enemy Block (+1 Spark/Ash with `Hollow Crown`).
  - `Stone`: unswappable/untappable; breaks via bounded (`MAX_STONE_CHAIN_STEPS = 7`) orthogonal chain ONLY on `>= 4` hue/Prism matches (or `3+` under `Brittle Quarry`).
  - `Sealed Columns (ADR-012)`: Columns in `board.sealedColumns` block swaps, taps, BFS expansion, gravity, refill, Chisel Wand, card paint/shatter, and Wall rise for `N` turns. `ensurePlayableSwap` strictly preserves all active `sealedColumns` durations whenever `>= 1` unsealed column exists (falling back to a vertical bent-3 or Prism+Playable pair inside the unsealed column), unsealing a single shortest-duration column ONLY if all 7 columns are sealed.
  - `Branded Blocks (ADR-010)`: Tiles with `isBranded = true` cost `2 Vigil` each when cleared (deducted from `combat.player.vigil`, any shortfall from `run.hp`), previewed before execution.
  - `Fog (ADR-011)`: `fogTapsRemaining > 0` hides tile hue colors and Ink pool values in UI for 3 Moves while preserving glyphs; `Gold Eye` (`clearsFog = true`) restores visibility immediately.
- **No-Move Policy:** Deterministic RNG swaps across unsealed occupied cells, falling back to `BoardPaintDeck` discard-and-redraw and deterministic unsealed-column rescue with an emitted `CombatEvent`.
- **Wall Overflow (`ADR-008`):** Checked BEFORE Wall rise. If all unsealed cells in row 0 are non-`Empty`:
  1. Lose `8` Vigil/HP (`run.hp` reduced by 8, unmitigated by Guard).
  2. Burn top two rows (`rows 0..1` in unsealed columns discarded to `BoardPaintDeck`, replaced with `TileKind.Empty`).
  3. Lose `1` Flask (`run.flasks` reduced by 1; hitting 0 triggers `GameOverReason.ZERO_FLASKS`).
  4. Skip Wall rise this turn.

## 3. Spectrum (`ADR-005`) & Echo (`ADR-015`)
- **Spectrum Wheel:** `Ember -> Gold -> Moss -> Tide -> Violet -> Ember`.
- Begins turn at Run 0 (`halfSteps = 2 = 1.0x`, `currentHue = null`). First pop establishes wheel anchor at Run 0 (`1.0x`); consecutive next-wheel hue increments Run (`1.5x`, `2.0x`, ...). Out-of-order or repeated hue computes at Run 0 (`1.0x`) then resets to `currentHue = null, runLength = 0`.
- **Echo (`ADR-015`):** Repeats ONLY `CardDef.primaryEffect` once; does not repeat costs, secondary statuses, exhaust, Flask gain, or card draw; Echo-of-Echo never triggers.

## 4. Transactional Command Guards
Every `GameCommand` runs through `GameEngine.validateTransition(run, command)` first; rejected commands return `accepted = false` with identical unchanged `RunState` (aside from `UseFlask` checking `ZERO_FLASKS` collapse if already 0).
